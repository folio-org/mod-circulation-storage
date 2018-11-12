package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LoanPolicies;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.resource.LoanPolicyStorage;
import org.folio.rest.persist.Criteria.Criteria;
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

public class LoanPoliciesAPI implements LoanPolicyStorage {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

        postgresClient.mutate(String.format("TRUNCATE TABLE %s_%s.%s",
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
                      LoanPolicyStorage.GetLoanPolicyStorageLoanPoliciesResponse.
                        respond200WithApplicationJson(pagedLoans)));
                  }
                  else {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      LoanPolicyStorage.GetLoanPolicyStorageLoanPoliciesResponse.
                        respond500WithTextPlain(reply.cause().getMessage())));
                  }
                } catch (Exception e) {
                  log.error(e);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    LoanPolicyStorage.GetLoanPolicyStorageLoanPoliciesResponse.
                      respond500WithTextPlain(e.getMessage())));
                }
              });
          } catch (Exception e) {
            log.error(e);
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              LoanPolicyStorage.GetLoanPolicyStorageLoanPoliciesResponse.
                respond500WithTextPlain(e.getMessage())));
          }
        });
      } catch (Exception e) {
        log.error(e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          LoanPolicyStorage.GetLoanPolicyStorageLoanPoliciesResponse.
            respond500WithTextPlain(e.getMessage())));
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
                      LoanPolicyStorage.PostLoanPolicyStorageLoanPoliciesResponse
                        .respond201WithApplicationJson(entity,
                          PostLoanPolicyStorageLoanPoliciesResponse.headersFor201().withLocation(reply.result()))));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      LoanPolicyStorage.PostLoanPolicyStorageLoanPoliciesResponse
                        .respond500WithTextPlain(reply.cause().toString())));
                }
              } catch (Exception e) {
                log.error(e);
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    LoanPolicyStorage.PostLoanPolicyStorageLoanPoliciesResponse
                      .respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanPolicyStorage.PostLoanPolicyStorageLoanPoliciesResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanPolicyStorage.PostLoanPolicyStorageLoanPoliciesResponse
          .respond500WithTextPlain(e.getMessage())));
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
                  @SuppressWarnings("unchecked")
                  List<LoanPolicy> loanPolicies = (List<LoanPolicy>) reply.result().getResults();

                  if (loanPolicies.size() == 1) {
                    LoanPolicy loanPolicy = loanPolicies.get(0);

                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        LoanPolicyStorage.
                          GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                          respond200WithApplicationJson(loanPolicy)));
                  }
                  else {
                    asyncResultHandler.handle(
                      Future.succeededFuture(
                        LoanPolicyStorage.
                          GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                          respond404WithTextPlain("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      LoanPolicyStorage.
                        GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                        respond500WithTextPlain(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                log.error(e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  LoanPolicyStorage.
                    GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanPolicyStorage.
              GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanPolicyStorage.
          GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.
          respond500WithTextPlain(e.getMessage())));
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

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(loanPolicyId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete(LOAN_POLICY_TABLE, criterion,
            reply -> {
              if(reply.succeeded()) {
                asyncResultHandler.handle(
                  Future.succeededFuture(
                    DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                      .respond204()));
              }
              else {
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                    .respond500WithTextPlain(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
          .respond500WithTextPlain(e.getMessage())));
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

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(loanPolicyId);

      Criterion criterion = new Criterion(a);

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
                                  .respond204()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                  .respond500WithTextPlain(
                                    update.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                .respond500WithTextPlain(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                        .respond500WithTextPlain(e.getMessage())));
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
                                  .respond204()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                  .respond500WithTextPlain(
                                    save.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                                .respond500WithTextPlain(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                        .respond500WithTextPlain(e.getMessage())));
                  }
                }
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
                    .respond500WithTextPlain(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }
}
