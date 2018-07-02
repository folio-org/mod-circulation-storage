package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.annotations.Validate;
import org.folio.rest.impl.support.DatabaseIdentity;
import org.folio.rest.jaxrs.model.LoanPolicies;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.resource.LoanPolicyStorageResource;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import javax.ws.rs.core.Response;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class LoanPoliciesAPI implements LoanPolicyStorageResource {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String LOAN_POLICY_TABLE = "loan_policy";
  private static final String IDENTITY_FIELD_NAME = "_id";

  private static final Class<LoanPolicy> LOAN_POLICY_CLASS = LoanPolicy.class;

  private static final DatabaseIdentity databaseIdentity = new DatabaseIdentity(
    IDENTITY_FIELD_NAME);

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

        postgresClient.mutate(String.format("TRUNCATE TABLE %s_%s.%s",
          tenantId, "mod_circulation_storage", LOAN_POLICY_TABLE),
          reply -> {
          try {
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              LoanPolicyStorageResource.DeleteLoanPolicyStorageLoanPoliciesResponse
                .noContent().build()));
          }
          catch (Exception e) {
            log.error("Failed to handle database response when deleting all loan policies", e);
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              LoanPolicyStorageResource.DeleteLoanPolicyStorageLoanPoliciesResponse
                .withPlainInternalServerError(e.getMessage())));
          }
        });
      }
      catch(Exception e) {
        log.error("Failed to make database request when deleting all loans", e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          LoanPolicyStorageResource.DeleteLoanPolicyStorageLoanPoliciesResponse
            .withPlainInternalServerError(e.getMessage())));
      }
    });
  }

  @Override
  public void getLoanPolicyStorageLoanPolicies(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

          String[] fieldList = {"*"};

          CQL2PgJSON cql2pgJson = new CQL2PgJSON("loan_policy.jsonb");
          CQLWrapper cql = new CQLWrapper(cql2pgJson, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));

          postgresClient.get(LOAN_POLICY_TABLE, LOAN_POLICY_CLASS, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  @SuppressWarnings("unchecked")
                  List<LoanPolicy> loanPolicies = (List<LoanPolicy>) reply.result().getResults();

                  LoanPolicies pagedLoans = new LoanPolicies();
                  pagedLoans.setLoanPolicies(loanPolicies);
                  pagedLoans.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    LoanPolicyStorageResource.GetLoanPolicyStorageLoanPoliciesResponse.
                      withJsonOK(pagedLoans)));
                }
                else {
                  log.error("Failed to get loan policies", reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    LoanPolicyStorageResource.GetLoanPolicyStorageLoanPoliciesResponse.
                      withPlainInternalServerError(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error("Failed to handle database response when getting loan policies", e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  LoanPolicyStorageResource.GetLoanPolicyStorageLoanPoliciesResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error("Failed to make database request when getting loan policies", e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanPolicyStorageResource.GetLoanPolicyStorageLoanPoliciesResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error("Failed to run operation on context when getting loan policies", e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanPolicyStorageResource.GetLoanPolicyStorageLoanPoliciesResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void postLoanPolicyStorageLoanPolicies(
    String lang, LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

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

          postgresClient.save(LOAN_POLICY_TABLE, entity.getId(), entity,
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
                  log.error("Failed to create a loan policy", reply.cause());
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
                        .withPlainInternalServerError(reply.cause().toString())));
                }
              } catch (Exception e) {
                log.error("Failed to handle database response when creating loan policy", e);
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
                      .withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error("Failed to make database request when creating loan policy", e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error("Failed to run operation on context when creating loan policy", e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanPolicyStorageResource.PostLoanPolicyStorageLoanPoliciesResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void getLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criterion criterion = databaseIdentity.queryBy(loanPolicyId);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(LOAN_POLICY_TABLE, LOAN_POLICY_CLASS, criterion, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  @SuppressWarnings("unchecked")
                  List<LoanPolicy> loanPolicies = (List<LoanPolicy>) reply.result().getResults();

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
                  log.error("Failed to get a loan policy", reply.cause());
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      LoanPolicyStorageResource.
                        GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                        withPlainInternalServerError(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                log.error("Failed to handle database response when getting a loan policy", e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  LoanPolicyStorageResource.
                    GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error("Failed to make database request when getting a loan policy", e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanPolicyStorageResource.
              GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error("Failed to run operation on context when getting a loan policy", e);
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
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criterion criterion = databaseIdentity.queryBy(loanPolicyId);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete(LOAN_POLICY_TABLE, criterion,
            reply -> {
            try {
              if(reply.succeeded()) {
                asyncResultHandler.handle(
                  Future.succeededFuture(
                    DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                      .withNoContent()));
              }
              else {
                log.error("Failed to delete a loan policy", reply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
              }
            }
            catch (Exception e) {
              log.error("Failed to handle database response when deleting a loan policy", e);
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                  .withPlainInternalServerError(e.getMessage())));
            }
            });
        } catch (Exception e) {
          log.error("Failed to make database request when deleting a loan policy", e);
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error("Failed to run operation on context when deleting a loan policy", e);
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
          .withPlainInternalServerError(e.getMessage())));
    }
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

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criterion criterion = databaseIdentity.queryBy(loanPolicyId);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(LOAN_POLICY_TABLE, LOAN_POLICY_CLASS, criterion, true, false,
            reply -> {
              if(reply.succeeded()) {
                @SuppressWarnings("unchecked")
                List<LoanPolicy> loanPolicyList = (List<LoanPolicy>) reply.result().getResults();

                if (loanPolicyList.size() == 1) {
                  try {
                    postgresClient.update(LOAN_POLICY_TABLE, entity, criterion,
                      true,
                      update -> {
                        try {
                          if(update.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                  .withNoContent()));
                          }
                          else {
                            log.error("Failed to replace a loan policy", reply.cause());
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                  .withPlainInternalServerError(
                                    update.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          log.error("Failed to handle database response when replacing a loan policy", e);
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    log.error("Failed to replace a loan policy", reply.cause());
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                        .withPlainInternalServerError(e.getMessage())));
                  }
                }
                else {
                  try {
                    postgresClient.save(LOAN_POLICY_TABLE, entity.getId(), entity,
                      save -> {
                        try {
                          if(save.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                  .withNoContent()));
                          }
                          else {
                            log.error("Failed to create a loan policy", reply.cause());
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                  .withPlainInternalServerError(
                                    save.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          log.error("Failed to handle database response when creating a loan policy", e);
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    log.error("Failed to create a loan policy", reply.cause());
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                        .withPlainInternalServerError(e.getMessage())));
                  }
                }
              } else {
                log.error("Failed to get loan policy whilst creating or replacing a loan policy", reply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                  PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          log.error("Failed to run operation on context when replacing a loan policy", e);
          asyncResultHandler.handle(Future.succeededFuture(
            PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error("Failed when replacing a loan policy", e);
      asyncResultHandler.handle(Future.succeededFuture(
        PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }
}
