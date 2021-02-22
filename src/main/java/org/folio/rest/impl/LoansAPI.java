package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.rest.impl.Headers.TENANT_HEADER;
import static org.folio.support.ModuleConstants.LOAN_CLASS;
import static org.folio.support.ModuleConstants.LOAN_HISTORY_TABLE;
import static org.folio.support.ModuleConstants.LOAN_TABLE;
import static org.folio.support.ModuleConstants.MODULE_NAME;
import static org.folio.support.ModuleConstants.OPEN_LOAN_STATUS;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.folio.rest.annotations.Validate;
import org.folio.rest.impl.util.OkapiResponseUtil;
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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class LoansAPI implements LoanStorage {
  private static final Logger log = LogManager.getLogger();

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

      postgresClient.execute(combinedAnonymizationSql, ResultHandlerFactory.when(
        s -> responseHandler.handle(succeededFuture(
          PostLoanStorageLoansAnonymizeByUserIdResponse.respond204())),
        serverErrorResponder::withError));
    });
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
        if (isMultipleOpenLoanErrorOnUpsert(reply)) {
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

  // Remove/Replace this function when MyPgUtil.putUpsert204() is removed/replaced.
  private boolean isMultipleOpenLoanErrorOnUpsert(AsyncResult<Response> reply) {
    return reply.succeeded()
      && reply.result().getStatus() == HTTP_BAD_REQUEST.toInt()
      && reply.result().hasEntity()
      && reply.result().getEntity().toString().contains("loan_itemid_idx_unique");
  }

  private boolean isMultipleOpenLoanError(AsyncResult<Response> reply) {
    return OkapiResponseUtil.containsErrorMessage(
      reply, "value already exists in table loan: ");
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
