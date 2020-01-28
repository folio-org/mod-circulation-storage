package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LoanPolicies;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.resource.LoanPolicyStorage;
import org.folio.rest.persist.MyPgUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.RTFConsts;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
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
        PostLoanPolicyStorageLoanPoliciesResponse.class, reply -> {
      if (isInvalidUUIDError(reply)) {
        asyncResultHandler.handle(
          Future.succeededFuture(LoanPolicyStorage.PostLoanPolicyStorageLoanPoliciesResponse
            .respond422WithApplicationJson(invalidUUIDError(entity))));
        return;
      }

      asyncResultHandler.handle(reply);
    });
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

  private Errors invalidUUIDError(LoanPolicy entity) {
    return ValidationHelper.createValidationErrorMessage(
      "itemId", entity.getId(),
      "Provided UUID for the lone policy is invalid");
  }

  private boolean isInvalidUUIDError(AsyncResult<Response> reply) {
    String message = "";

    if (reply.succeeded() && reply.result().getStatus() >= 400 &&
      reply.result().getStatus() < 600 && reply.result().hasEntity()) {

      // When entity is an instance of Errors, the getEntity().toString()
      // will return an object identifier. Process the object to correctly
      // parse the message.
      if (reply.result().getEntity() instanceof Errors) {
        Errors errors = (Errors) reply.result().getEntity();

        for (int i = 0; i < errors.getErrors().size(); i++) {
          Error error = errors.getErrors().get(i);

          if (error.getType() == RTFConsts.VALIDATION_FIELD_ERROR)
            message = error.getMessage().toLowerCase();
        }
      } else {
        message = reply.result().getEntity().toString().toLowerCase();
      }
    }

    return message.contains("invalid input syntax for type uuid: ") ||
      message.contains("duplicate key value violates unique constraint ");
  }
}
