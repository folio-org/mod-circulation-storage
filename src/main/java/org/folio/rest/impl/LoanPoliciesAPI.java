package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.resource.LoanPolicyStorageResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class LoanPoliciesAPI implements LoanPolicyStorageResource {

  private final String LOAN_POLICY_TABLE = "loan_policy";
  private final Class<LoanPolicy> LOAN_POLICY_CLASS = LoanPolicy.class;

  @Override
  @Validate
  public void deleteLoanPolicyStorageLoanPolicies(
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    asyncResultHandler.handle(succeededFuture(Response.status(501).build()));
  }

  @Override
  @Validate
  public void getLoanPolicyStorageLoanPolicies(
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    asyncResultHandler.handle(succeededFuture(Response.status(501).build()));
  }

  @Override
  @Validate
  public void postLoanPolicyStorageLoanPolicies(
    String lang, LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {
          if(entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
          }

          postgresClient.save("loan_policy", entity.getId(), entity,
            reply -> {
              try {
                if(reply.succeeded()) {
                  OutStream stream = new OutStream();
                  stream.setData(entity);

                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
                        .withJsonCreated(reply.result(), stream)));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
                        .withPlainInternalServerError(reply.cause().toString())));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
                      .withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void getLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(loanPolicyId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(LOAN_POLICY_TABLE, LOAN_POLICY_CLASS, criterion, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<LoanPolicy> loanPolicies = (List<LoanPolicy>) reply.result()[0];

                  if (loanPolicies.size() == 1) {
                    LoanPolicy loanPolicy = loanPolicies.get(0);

                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        LoanPolicyStorageResource.
                          GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                          withJsonOK(loanPolicy)));
                  }
                  else {
                    asyncResultHandler.handle(
                      Future.succeededFuture(
                        LoanPolicyStorageResource.
                          GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                          withPlainNotFound("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      LoanPolicyStorageResource.
                        GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                        withPlainInternalServerError(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  LoanPolicyStorageResource.
                    GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanPolicyStorageResource.
              GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanPolicyStorageResource.
          GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void deleteLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    asyncResultHandler.handle(succeededFuture(Response.status(501).build()));
  }

  @Override
  @Validate
  public void putLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang, LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    asyncResultHandler.handle(succeededFuture(Response.status(501).build()));
  }
}
