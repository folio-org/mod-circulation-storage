package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.resource.LoanStorage;
import org.folio.service.loan.LoanService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class LoansAPI implements LoanStorage {

  @Validate
  @Override
  public void deleteLoanStorageLoans(String query, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).deleteByCql(query)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getLoanStorageLoans(String totalRecords, int offset, int limit, String query,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).findByQuery(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postLoanStorageLoans(Loan loan, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

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
  public void getLoanStorageLoansByLoanId(String loanId, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).findById(loanId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteLoanStorageLoansByLoanId(String loanId, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).deleteById(loanId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void putLoanStorageLoansByLoanId(String loanId, Loan loan,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).createOrUpdate(loanId, loan)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getLoanStorageLoanHistory(String totalRecords, int offset, int limit, String query,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).getLoanHistory(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

}
