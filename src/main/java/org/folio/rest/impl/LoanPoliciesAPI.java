package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LoanPolicies;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.resource.LoanPolicyStorage;
import org.folio.rest.persist.MyPgUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import javax.ws.rs.core.Response;
import java.util.Map;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class LoanPoliciesAPI implements LoanPolicyStorage {
  private static final String LOAN_POLICY_TABLE = "loan_policy";
  private static final Class<LoanPolicy> LOAN_POLICY_CLASS = LoanPolicy.class;

  @Override
  @Validate
  public void deleteLoanPolicyStorageLoanPolicies(
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("TRUNCATE TABLE %s_%s.%s",
          tenantId, "mod_circulation_storage", LOAN_POLICY_TABLE),
          reply -> asyncResultHandler.handle(Future.succeededFuture(
            DeleteLoanPolicyStorageLoanPoliciesResponse.respond204())));
      }
      catch(Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          LoanPolicyStorage.DeleteLoanPolicyStorageLoanPoliciesResponse
            .respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  @Override
  @Validate
  public void getLoanPolicyStorageLoanPolicies(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.get(LOAN_POLICY_TABLE,  LOAN_POLICY_CLASS, LoanPolicies.class, query, offset, limit,
        okapiHeaders, vertxContext,
        GetLoanPolicyStorageLoanPoliciesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postLoanPolicyStorageLoanPolicies(
    String lang, LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.post(LOAN_POLICY_TABLE, entity, okapiHeaders, vertxContext,
        PostLoanPolicyStorageLoanPoliciesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(LOAN_POLICY_TABLE, LOAN_POLICY_CLASS, loanPolicyId, okapiHeaders, vertxContext,
        GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(LOAN_POLICY_TABLE, loanPolicyId, okapiHeaders, vertxContext,
        DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang,
    LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    MyPgUtil.putUpsert204(LOAN_POLICY_TABLE, entity, loanPolicyId, okapiHeaders, vertxContext,
        PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.class, asyncResultHandler);
  }
}
