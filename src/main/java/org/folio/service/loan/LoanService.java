package org.folio.service.loan;

import static io.vertx.core.Future.succeededFuture;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.service.event.EntityChangedEventPublisherFactory.loanEventPublisher;
import static org.folio.support.ModuleConstants.LOAN_CLASS;
import static org.folio.support.ModuleConstants.LOAN_HISTORY_TABLE;
import static org.folio.support.ModuleConstants.LOAN_TABLE;
import static org.folio.support.ModuleConstants.MODULE_NAME;
import static org.folio.support.ModuleConstants.OPEN_LOAN_STATUS;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

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
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.service.event.EntityChangedEventPublisher;
import org.folio.support.ResultHandlerFactory;
import org.folio.support.ServerErrorResponder;
import org.folio.support.UUIDValidation;
import org.folio.support.VertxContextRunner;

public class LoanService {

  private static final Logger log = LogManager.getLogger();

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final EntityChangedEventPublisher<String, Loan> eventPublisher;


  public LoanService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    this.postgresClient = postgresClient(vertxContext, okapiHeaders);
    this.eventPublisher = loanEventPublisher(vertxContext, okapiHeaders);
  }

  public Future<Response> findByQuery(String query, int offset, int limit) {
    return PgUtil.get(LOAN_TABLE, LOAN_CLASS, Loans.class, query, offset, limit, okapiHeaders, vertxContext,
        LoanStorage.GetLoanStorageLoansResponse.class);
  }

  public Future<Response> findById(String loanId) {
    return PgUtil.getById(LOAN_TABLE, LOAN_CLASS, loanId, okapiHeaders, vertxContext,
        LoanStorage.GetLoanStorageLoansByLoanIdResponse.class);
  }

  public Future<Response> create(Loan loan) {
    if (loan.getStatus() == null) {
      loan.setStatus(new Status().withName(OPEN_LOAN_STATUS));
    }

    if (isOpenAndHasNoUserId(loan)) {
      return respondWithError(
          LoanStorage.PostLoanStorageLoansResponse::respond422WithApplicationJson,
          "Open loan must have a user ID");
    }

    //TODO: Convert this to use validation responses (422 and error of errors)
    ImmutablePair<Boolean, String> validationResult = validateLoan(loan);

    if (!validationResult.getLeft()) {
      return succeededFuture(LoanStorage.PostLoanStorageLoansResponse
          .respond400WithTextPlain(validationResult.getRight()));
    }

    Promise<Response> promise = Promise.promise();

    PgUtil.post(LOAN_TABLE, loan, okapiHeaders, vertxContext,
        LoanStorage.PostLoanStorageLoansResponse.class, reply -> {
          if (isMultipleOpenLoanError(reply)) {
            promise.complete(LoanStorage.PostLoanStorageLoansResponse
                .respond422WithApplicationJson(moreThanOneOpenLoanError(loan)));
          } else {
            promise.handle(reply);
          }
        });

    return promise.future()
        .compose(eventPublisher.publishCreated());
  }

  public Future<Response> update(String loanId, Loan loan) {
    if (loan.getStatus() == null) {
      loan.setStatus(new Status().withName(OPEN_LOAN_STATUS));
    }

    ImmutablePair<Boolean, String> validationResult = validateLoan(loan);

    if (!validationResult.getLeft()) {
      return succeededFuture(LoanStorage.PutLoanStorageLoansByLoanIdResponse
                  .respond400WithTextPlain(validationResult.getRight()));
    }

    if (isOpenAndHasNoUserId(loan)) {
      return respondWithError(
          LoanStorage.PutLoanStorageLoansByLoanIdResponse::respond422WithApplicationJson,
          "Open loan must have a user ID");
    }

    Promise<Response> promise = Promise.promise();

    MyPgUtil.putUpsert204(LOAN_TABLE, loan, loanId, okapiHeaders, vertxContext,
        LoanStorage.PutLoanStorageLoansByLoanIdResponse.class, reply -> {
          if (isMultipleOpenLoanErrorOnUpsert(reply)) {
                promise.complete(LoanStorage.PutLoanStorageLoansByLoanIdResponse
                      .respond422WithApplicationJson(moreThanOneOpenLoanError(loan)));
          } else {
            promise.handle(reply);
          }
        });

    return promise.future();
  }

  public Future<Response> delete(String loanId) {
    return PgUtil.deleteById(LOAN_TABLE, loanId, okapiHeaders, vertxContext,
        LoanStorage.DeleteLoanStorageLoansByLoanIdResponse.class);
  }

  public Future<Response> deleteAll() {
    Promise<Response> promise = Promise.promise();

    try {
      postgresClient.execute(String.format("TRUNCATE TABLE %s_%s.loan", tenantId(okapiHeaders), MODULE_NAME),
          result -> promise.complete(result.succeeded()
              ? LoanStorage.DeleteLoanStorageLoansResponse.respond204()
              : LoanStorage.DeleteLoanStorageLoansResponse.respond500WithTextPlain(result.cause().getMessage())));
    } catch (Exception e) {
      promise.complete(LoanStorage.DeleteLoanStorageLoansResponse
          .respond500WithTextPlain(e.getMessage()));
    }

    return promise.future()
        .compose(eventPublisher.publishAllRemoved());
  }

  public Future<Response> getLoanHistory(String query, int offset, int limit) {
    String cql = query;
    if (StringUtils.isBlank(cql)) {
      cql = "cql.allRecords=1";
    }
    if (!cql.toLowerCase().contains(" sortby ")) {
      cql += " sortBy createdDate/sort.descending";
    }

    return PgUtil.get(LOAN_HISTORY_TABLE, LoansHistoryItem.class, LoansHistoryItems.class, cql, offset, limit,
        okapiHeaders, vertxContext, LoanStorage.GetLoanStorageLoanHistoryResponse.class);
  }

  public Future<Response> anonymizeByUserId(String userId) {
    Promise<Response> promise = Promise.promise();

    final ServerErrorResponder serverErrorResponder =
        new ServerErrorResponder(LoanStorage.PostLoanStorageLoansAnonymizeByUserIdResponse
            ::respond500WithTextPlain, promise, log);

    final VertxContextRunner runner = new VertxContextRunner(
        vertxContext, serverErrorResponder::withError);

    runner.runOnContext(() -> {
      if (!UUIDValidation.isValidUUID(userId)) {
        final Errors errors = ValidationHelper.createValidationErrorMessage(
            "userId", userId, "Invalid user ID, should be a UUID");

        promise.complete(LoanStorage.PostLoanStorageLoansAnonymizeByUserIdResponse
                .respond422WithApplicationJson(errors));
        return;
      }

      final String combinedAnonymizationSql = createAnonymizationSQL(userId, tenantId(okapiHeaders));

      log.info(String.format("Anonymization SQL: %s", combinedAnonymizationSql));

      postgresClient.execute(combinedAnonymizationSql, ResultHandlerFactory.when(
          s -> promise.complete(LoanStorage.PostLoanStorageLoansAnonymizeByUserIdResponse.respond204()),
          serverErrorResponder::withError));
    });

    return promise.future();
  }

  private boolean isOpenAndHasNoUserId(Loan loan) {
    return Objects.equals(loan.getStatus().getName(), OPEN_LOAN_STATUS)
        && loan.getUserId() == null;
  }

  private ImmutablePair<Boolean, String> validateLoan(Loan loan) {
    boolean valid = true;
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
  private boolean isMultipleOpenLoanError(AsyncResult<Response> reply) {
    return OkapiResponseUtil.containsErrorMessage(
        reply, "value already exists in table loan: ");
  }

  // Remove/Replace this function when MyPgUtil.putUpsert204() is removed/replaced.
  private boolean isMultipleOpenLoanErrorOnUpsert(AsyncResult<Response> reply) {
    return reply.succeeded()
        && reply.result().getStatus() == HTTP_BAD_REQUEST.toInt()
        && reply.result().hasEntity()
        && reply.result().getEntity().toString().contains("loan_itemid_idx_unique");
  }

  private Errors moreThanOneOpenLoanError(Loan entity) {
    return ValidationHelper.createValidationErrorMessage(
        "itemId", entity.getItemId(),
        "Cannot have more than one open loan for the same item");
  }

  private Future<Response> respondWithError(Function<Errors, Response> responseCreator, String message) {
    final ArrayList<Error> errorsList = new ArrayList<>();

    errorsList.add(new Error().withMessage(message));

    final Errors errors = new Errors().withErrors(errorsList);

    return respondWithErrors(responseCreator, errors);
  }

  private Future<Response> respondWithErrors(Function<Errors, Response> responseCreator, Errors errors) {
    return succeededFuture(responseCreator.apply(errors));
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
