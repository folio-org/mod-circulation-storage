package org.folio.service.loan;

import static io.vertx.core.Future.succeededFuture;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.LOAN_CLASS;
import static org.folio.support.ModuleConstants.LOAN_TABLE;
import static org.folio.support.ModuleConstants.OPEN_LOAN_STATUS;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.joda.time.DateTime;

import org.folio.rest.impl.util.OkapiResponseUtil;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Loans;
import org.folio.rest.jaxrs.model.Status;
import org.folio.rest.jaxrs.resource.LoanStorage;
import org.folio.rest.persist.MyPgUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ValidationHelper;

public class LoanService {

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;

  public LoanService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    final PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
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

    var response = PgUtil.post(LOAN_TABLE, loan, okapiHeaders, vertxContext,
          LoanStorage.PostLoanStorageLoansResponse.class);

    if (isMultipleOpenLoanError(response)) {
      response = respondWithErrors(
          LoanStorage.PostLoanStorageLoansResponse::respond422WithApplicationJson,
          moreThanOneOpenLoanError(loan));
    }

    return response;
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

  private boolean isOpenAndHasNoUserId(Loan loan) {
    return Objects.equals(loan.getStatus().getName(), OPEN_LOAN_STATUS)
        && loan.getUserId() == null;
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

}