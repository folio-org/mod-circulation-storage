package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.resource.LoanPolicyStorageResource;

import javax.ws.rs.core.Response;
import java.util.Map;

public class LoanPolicyAPI implements LoanPolicyStorageResource {

  @Override
  public void deleteLoanPolicyStorageLoanPolicies(
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {



  }

  @Override
  public void getLoanPolicyStorageLoanPolicies(
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

  }

  @Override
  public void postLoanPolicyStorageLoanPolicies(
    String lang, LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {



  }

  @Override
  public void getLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {



  }

  @Override
  public void deleteLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {



  }

  @Override
  public void putLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang, LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {



  }
}
