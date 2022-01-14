package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.resource.LoanStorage;
import org.folio.service.loan.LoanService;

public class LoansAPI implements LoanStorage {

  @Validate
  @Override
  public void deleteLoanStorageLoans(
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).deleteAll()
        .onComplete(asyncResultHandler);
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

    new LoanService(vertxContext, okapiHeaders).findByQuery(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postLoanStorageLoans(
    String lang,
    Loan loan,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).create(loan)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postLoanStorageLoansAnonymizeByUserId(
    String userId,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> responseHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).anonymizeByUserId(userId)
        .onComplete(responseHandler);
  }

  @Validate
  @Override
  public void getLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).findById(loanId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).delete(loanId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void putLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Loan loan,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).update(loanId, loan)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getLoanStorageLoanHistory(int offset, int limit, String query, String lang,
                                        Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).getLoanHistory(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

}
