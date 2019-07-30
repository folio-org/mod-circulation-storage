package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AnonymizeLoansRequest;
import org.folio.rest.jaxrs.model.AnonymizeLoansResponse;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Loans;
import org.folio.rest.jaxrs.model.LoansHistoryItem;
import org.folio.rest.jaxrs.model.LoansHistoryItems;
import org.folio.rest.jaxrs.model.Status;
import org.folio.rest.jaxrs.resource.LoanStorage;
import org.folio.rest.persist.MyPgUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.support.ResultHandlerFactory;
import org.folio.support.ServerErrorResponder;
import org.folio.support.UUIDValidation;
import org.folio.support.VertxContextRunner;
import org.joda.time.DateTime;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class LoansAPI implements LoanStorage {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String MODULE_NAME = "mod_circulation_storage";
  private static final String LOAN_TABLE = "loan";
  //TODO: Change loan history table name when can be configured, used to be "loan_history_table"
  private static final String LOAN_HISTORY_TABLE = "audit_loan";

  private static final Class<Loan> LOAN_CLASS = Loan.class;
  private static final String OPEN_LOAN_STATUS = "Open";

  @Override
  public void deleteLoanStorageLoans(
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("TRUNCATE TABLE %s_%s.loan",
          tenantId, MODULE_NAME),
          reply -> asyncResultHandler.handle(succeededFuture(
            DeleteLoanStorageLoansResponse.respond204())));
      } catch (Exception e) {
        asyncResultHandler.handle(succeededFuture(
          LoanStorage.DeleteLoanStorageLoansResponse
            .respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  @Validate
  @Override
  public void getLoanStorageLoans(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.get(LOAN_TABLE, LOAN_CLASS, Loans.class, query, offset, limit, okapiHeaders, vertxContext,
      GetLoanStorageLoansResponse.class, asyncResultHandler);
  }

  @Override
  public void postLoanStorageLoans(
    String lang,
    Loan loan,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (loan.getStatus() == null) {
      loan.setStatus(new Status().withName(OPEN_LOAN_STATUS));
    }

    if (isOpenAndHasNoUserId(loan)) {
      respondWithError(asyncResultHandler,
        PostLoanStorageLoansResponse::respond422WithApplicationJson,
        "Open loan must have a user ID");
      return;
    }

    //TODO: Convert this to use validation responses (422 and error of errors)
    ImmutablePair<Boolean, String> validationResult = validateLoan(loan);

    if (!validationResult.getLeft()) {
      asyncResultHandler.handle(
        succeededFuture(
          LoanStorage.PostLoanStorageLoansResponse
            .respond400WithTextPlain(
              validationResult.getRight())));

      return;
    }

    PgUtil.post(LOAN_TABLE, loan, okapiHeaders, vertxContext, PostLoanStorageLoansResponse.class, reply -> {
      if (isMultipleOpenLoanError(reply)) {
        asyncResultHandler.handle(
          succeededFuture(LoanStorage.PostLoanStorageLoansResponse
            .respond422WithApplicationJson(moreThanOneOpenLoanError(loan))));
        return;
      }
      asyncResultHandler.handle(reply);
    });
  }

  @Override
  public void postLoanStorageLoansAnonymizeByUserId(
    String userId,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> responseHandler,
    Context vertxContext) {

    final ServerErrorResponder serverErrorResponder =
      new ServerErrorResponder(PostLoanStorageLoansAnonymizeByUserIdResponse
        ::respond500WithTextPlain, responseHandler, log);

    final VertxContextRunner runner = new VertxContextRunner(
      vertxContext, serverErrorResponder::withError);

    runner.runOnContext(() -> {
      if (!UUIDValidation.isValidUUID(userId)) {
        final Errors errors = ValidationHelper.createValidationErrorMessage(
          "userId", userId, "Invalid user ID, should be a UUID");

        responseHandler.handle(succeededFuture(
          PostLoanStorageLoansAnonymizeByUserIdResponse
            .respond422WithApplicationJson(errors)));
        return;
      }

      final String tenantId = TenantTool.tenantId(okapiHeaders);

      final PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), tenantId);

      final String combinedAnonymizationSql = createAnonymizationSQL(userId,
        tenantId);

      log.info(String.format("Anonymization SQL: %s", combinedAnonymizationSql));

      postgresClient.mutate(combinedAnonymizationSql,
        new ResultHandlerFactory().when(
          s -> responseHandler.handle(succeededFuture(
            PostLoanStorageLoansAnonymizeByUserIdResponse.respond204())),
          serverErrorResponder::withError));
    });
  }

  @Override
  public void postLoanStorageAnonymizeLoans(AnonymizeLoansRequest request,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> responseHandler,
                                            Context vertxContext) {

    AnonymizeLoansResponse response = new AnonymizeLoansResponse();

    List<String> loanIds = request.getLoanIds();
    final Set<String> invalidUuids = loanIds.stream()
      .filter(id -> !UUIDValidation.isValidUUID(id))
      .collect(Collectors.toSet());

    if (!invalidUuids.isEmpty()) {
      final Errors errors = ValidationHelper.createValidationErrorMessage(
        "invalidLoanIds",
        Json.encode(invalidUuids),
        "Loan IDs should be a valid UUID");
      responseHandler.handle(succeededFuture(
        PostLoanStorageLoansAnonymizeByUserIdResponse
          .respond422WithApplicationJson(errors)));
      return;
    }

    final String tenantId = TenantTool.tenantId(okapiHeaders);
    final PostgresClient postgresClient = PgUtil.postgresClient(vertxContext,
      okapiHeaders);

    final String combinedAnonymizationSql = createAnonymizationSQL(loanIds,
      tenantId);
    log.info(String.format("Anonymization SQL: %s", combinedAnonymizationSql));

    executeSql(postgresClient, combinedAnonymizationSql)
      .map(updateResult -> PostLoanStorageAnonymizeLoansResponse.
        respond200WithApplicationJson(response.withUpdatedCount(
          updateResult.getUpdated())))
      .map(Response.class::cast)
      .otherwise(e ->
        PostLoanStorageAnonymizeLoansResponse.respond500WithTextPlain(e.getMessage()))
      .setHandler(responseHandler);
  }

  private Future<UpdateResult> executeSql(PostgresClient postgresClient, String sql) {
    Future<UpdateResult> future = Future.future();
    postgresClient.execute(sql, future);
    return future;
  }

  @Validate
  @Override
  public void getLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(LOAN_TABLE, LOAN_CLASS, loanId, okapiHeaders, vertxContext,
      GetLoanStorageLoansByLoanIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(LOAN_TABLE, loanId, okapiHeaders, vertxContext,
      DeleteLoanStorageLoansByLoanIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Loan loan,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (loan.getStatus() == null) {
      loan.setStatus(new Status().withName(OPEN_LOAN_STATUS));
    }

    ImmutablePair<Boolean, String> validationResult = validateLoan(loan);

    if (!validationResult.getLeft()) {
      asyncResultHandler.handle(
        succeededFuture(
          LoanStorage.PutLoanStorageLoansByLoanIdResponse
            .respond400WithTextPlain(
              validationResult.getRight())));

      return;
    }

    if (isOpenAndHasNoUserId(loan)) {
      respondWithError(asyncResultHandler,
        PutLoanStorageLoansByLoanIdResponse::respond422WithApplicationJson,
        "Open loan must have a user ID");
      return;
    }

    MyPgUtil.putUpsert204(LOAN_TABLE, loan, loanId, okapiHeaders, vertxContext,
      PutLoanStorageLoansByLoanIdResponse.class, reply -> {
        if (isMultipleOpenLoanError(reply)) {
          asyncResultHandler.handle(
            succeededFuture(
              LoanStorage.PutLoanStorageLoansByLoanIdResponse
                .respond422WithApplicationJson(
                  moreThanOneOpenLoanError(loan))));
          return;
        }
        asyncResultHandler.handle(reply);
      });
  }

  private ImmutablePair<Boolean, String> validateLoan(Loan loan) {

    Boolean valid = true;
    StringJoiner messages = new StringJoiner("\n");

    //ISO8601 is less strict than RFC3339 so will not catch some issues
    try {
      DateTime.parse(loan.getLoanDate());
    } catch (Exception e) {
      valid = false;
      messages.add("loan date must be a date time (in RFC3339 format)");
    }

    if (loan.getReturnDate() != null) {
      //ISO8601 is less strict than RFC3339 so will not catch some issues
      try {
        DateTime.parse(loan.getReturnDate());
      } catch (Exception e) {
        valid = false;
        messages.add("return date must be a date time (in RFC3339 format)");
      }
    }

    return new ImmutablePair<>(valid, messages.toString());
  }

  @Validate
  @Override
  public void getLoanStorageLoanHistory(int offset, int limit, String query, String lang,
                                        Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    String cql = query;
    if (StringUtils.isBlank(cql)) {
      cql = "cql.allRecords=1";
    }
    if (!cql.toLowerCase().contains(" sortby ")) {
      cql += " sortBy createdDate/sort.descending";
    }
    PgUtil.get(LOAN_HISTORY_TABLE, LoansHistoryItem.class, LoansHistoryItems.class, cql, offset, limit, okapiHeaders,
      vertxContext, GetLoanStorageLoanHistoryResponse.class, asyncResultHandler);
  }

  private Errors moreThanOneOpenLoanError(Loan entity) {
    return ValidationHelper.createValidationErrorMessage(
      "itemId", entity.getItemId(),
      "Cannot have more than one open loan for the same item");
  }

  private boolean isMultipleOpenLoanError(AsyncResult<Response> reply) {
    return reply.succeeded()
      && reply.result().getStatus() == 400
      && reply.result().getEntity().toString().contains("loan_itemid_idx_unique");
  }

  private boolean isOpenAndHasNoUserId(Loan loan) {
    return Objects.equals(loan.getStatus().getName(), OPEN_LOAN_STATUS)
      && loan.getUserId() == null;
  }

  private void respondWithError(
    Handler<AsyncResult<Response>> asyncResultHandler,
    Function<Errors, Response> responseCreator,
    String message) {

    final ArrayList<Error> errorsList = new ArrayList<>();

    errorsList.add(new Error().withMessage(message));

    final Errors errors = new Errors()
      .withErrors(errorsList);

    asyncResultHandler.handle(succeededFuture(
      responseCreator.apply(errors)));
  }

  private String createAnonymizationSQL(@NotNull Collection<String> loanIdList,
                                        String tenantId) {

    String loanIds = loanIdList.stream()
      .map(s -> "\'" + s + "\'")
      .collect(Collectors.joining(",", "(", ")"));

    final String anonymizeLoansSql = String.format(
      "UPDATE %s_%s.loan "
        + " SET jsonb = jsonb - 'userId'"
        + " WHERE loan.id in " + loanIds
        + " AND loan.jsonb->'status'->>'name' = 'Closed'"
        + "AND  loan.jsonb->'userId' is NOT null",
      tenantId, MODULE_NAME);

    //Only anonymize the history for loans that are currently closed
    //meaning that we need to refer to loans in this query
    final String anonymizeLoansActionHistorySql = String.format(
      "UPDATE %s_%s.%s l"
        + " SET jsonb = jsonb #- '{loan,userId}'"
        + " WHERE jsonb->'loan'->>'id' IN"
        + " (SELECT l.jsonb->>'id'"
        + " FROM %s_%s.loan l"
        + " WHERE l.id in " + loanIds
        + " AND l.jsonb->'status'->>'name' = 'Closed')"
        + "AND  l.jsonb->'userId' is NOT NULL",
      tenantId, MODULE_NAME, LOAN_HISTORY_TABLE,
      tenantId, MODULE_NAME);

    //Loan action history needs to go first, as needs to be for specific loans
    return anonymizeLoansActionHistorySql + "; " + anonymizeLoansSql;
  }

  private String createAnonymizationSQL(
    @NotNull String userId,
    String tenantId) {

    final String anonymizeLoansSql = String.format(
      "UPDATE %s_%s.loan"
        + " SET jsonb = jsonb - 'userId'"
        + " WHERE jsonb->>'userId' = '" + userId + "'"
        + " AND jsonb->'status'->>'name' = 'Closed'",
      tenantId, MODULE_NAME);

    //Only anonymize the history for loans that are currently closed
    //meaning that we need to refer to loans in this query
    final String anonymizeLoansActionHistorySql = String.format(
      "UPDATE %s_%s.%s"
        + " SET jsonb = jsonb #- '{loan,userId}'"
        + " WHERE jsonb->'loan'->>'id' IN"
        + "   (SELECT l.jsonb->>'id'"
        + "    FROM %s_%s.loan l"
        + "    WHERE l.jsonb->>'userId' = '" + userId + "'"
        + "      AND l.jsonb->'status'->>'name' = 'Closed')",
      tenantId, MODULE_NAME, LOAN_HISTORY_TABLE,
      tenantId, MODULE_NAME);

    //Loan action history needs to go first, as needs to be for specific loans
    return anonymizeLoansActionHistorySql + "; " + anonymizeLoansSql;
  }

}
