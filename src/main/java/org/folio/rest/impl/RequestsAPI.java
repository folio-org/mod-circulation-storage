package org.folio.rest.impl;

import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Requests;
import org.folio.rest.jaxrs.resource.LoanPolicyStorage;
import org.folio.rest.jaxrs.resource.RequestStorage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class RequestsAPI implements RequestStorage {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String REQUEST_TABLE = "request";

  @Override
  public void deleteRequestStorageRequests(
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("TRUNCATE TABLE %s_%s.%s",
          tenantId, "mod_circulation_storage", REQUEST_TABLE),
          reply -> asyncResultHandler.handle(succeededFuture(
            DeleteRequestStorageRequestsResponse.respond204())));
      }
      catch(Exception e) {
        asyncResultHandler.handle(succeededFuture(
          DeleteRequestStorageRequestsResponse
            .respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  @Override
  public void getRequestStorageRequests(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    Consumer<Exception> exceptionHandler = e -> {
      log.error("Getting requests failed", e);
      asyncResultHandler.handle(succeededFuture(
        GetRequestStorageRequestsByRequestIdResponse.
          respond500WithTextPlain(e.getMessage())));
    };

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

          String[] fieldList = {"*"};

          CQL2PgJSON cql2pgJson = new CQL2PgJSON(String.format("%s.jsonb", REQUEST_TABLE));
          CQLWrapper cql = new CQLWrapper(cql2pgJson, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));

          log.error(String.format("CQL query: %s", query));
          log.error(String.format("SQL generated from CQL: %s", cql.toString()));

          postgresClient.get(REQUEST_TABLE, Request.class, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  @SuppressWarnings("unchecked")
                  List<Request> requests = (List<Request>) reply.result().getResults();

                  Requests pagedRequests = new Requests();
                  pagedRequests.setRequests(requests);
                  pagedRequests.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(succeededFuture(
                    GetRequestStorageRequestsResponse.respond200WithApplicationJson(pagedRequests)));
                }
                else {
                  asyncResultHandler.handle(succeededFuture(
                    LoanPolicyStorage.GetLoanPolicyStorageLoanPoliciesResponse.
                      respond500WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                exceptionHandler.accept(e);
              }
            });
        } catch (Exception e) {
          exceptionHandler.accept(e);
        }
      });
    } catch (Exception e) {
      exceptionHandler.accept(e);
    }
  }

  @Override
  public void postRequestStorageRequests(
    String lang,
    Request entity,
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

          postgresClient.save(REQUEST_TABLE, entity.getId(), entity,
            reply -> {
              try {
                if(reply.succeeded()) {
                  OutStream stream = new OutStream();
                  stream.setData(entity);

                  asyncResultHandler.handle(succeededFuture(
                      PostRequestStorageRequestsResponse
                        .respond201WithApplicationJson(entity, 
                          PostRequestStorageRequestsResponse.headersFor201().withLocation(reply.result()))));
                }
                else {
                  if(isSamePositionInQueueError(reply)) {
                    asyncResultHandler.handle(succeededFuture(
                      PostRequestStorageRequestsResponse
                        .respond422WithApplicationJson(samePositionInQueueError(entity))));
                  }
                  else {
                    asyncResultHandler.handle(succeededFuture(
                      PostRequestStorageRequestsResponse
                        .respond500WithTextPlain(reply.cause().toString())));
                  }
                }
              } catch (Exception e) {
                asyncResultHandler.handle(succeededFuture(
                  PostRequestStorageRequestsResponse
                    .respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(succeededFuture(
            PostRequestStorageRequestsResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(succeededFuture(
        PostRequestStorageRequestsResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void getRequestStorageRequestsByRequestId(
    String requestId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    Consumer<Exception> exceptionHandler = e -> {
      log.error("Getting request by ID failed", e);
      asyncResultHandler.handle(succeededFuture(
        GetRequestStorageRequestsByRequestIdResponse.
          respond500WithTextPlain(e.getMessage())));
    };

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(requestId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(REQUEST_TABLE, Request.class, criterion, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<Request> requests = (List<Request>) reply.result().getResults();

                  if (requests.size() == 1) {
                    Request request = requests.get(0);

                    asyncResultHandler.handle(succeededFuture(
                      GetRequestStorageRequestsByRequestIdResponse.
                        respond200WithApplicationJson(request)));
                  }
                  else {
                    asyncResultHandler.handle(succeededFuture(
                      GetRequestStorageRequestsByRequestIdResponse.
                        respond404WithTextPlain("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(succeededFuture(
                    GetRequestStorageRequestsByRequestIdResponse.
                      respond500WithTextPlain(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                exceptionHandler.accept(e);
              }
            });
        } catch (Exception e) {
          exceptionHandler.accept(e);
        }
      });
    } catch (Exception e) {
      exceptionHandler.accept(e);
    }
  }

  @Override
  public void deleteRequestStorageRequestsByRequestId(
    String requestId,
    String lang,
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
      a.setValue(requestId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete(REQUEST_TABLE, criterion,
            reply -> {
              if(reply.succeeded()) {
                asyncResultHandler.handle(succeededFuture(
                  DeleteRequestStorageRequestsByRequestIdResponse
                    .respond204()));
              }
              else {
                asyncResultHandler.handle(succeededFuture(
                  DeleteRequestStorageRequestsByRequestIdResponse
                    .respond500WithTextPlain(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(succeededFuture(
            DeleteRequestStorageRequestsByRequestIdResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(succeededFuture(
        DeleteRequestStorageRequestsByRequestIdResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void putRequestStorageRequestsByRequestId(
    String requestId,
    String lang, Request entity,
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
      a.setValue(requestId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(REQUEST_TABLE, Request.class, criterion, true, false,
            reply -> {
              if(reply.succeeded()) {
                List<Request> requestList = (List<Request>) reply.result().getResults();

                if (requestList.size() == 1) {
                  try {
                    postgresClient.update(REQUEST_TABLE, entity, criterion,
                      true,
                      update -> {
                        try {
                          if(update.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(succeededFuture(
                              PutRequestStorageRequestsByRequestIdResponse
                                .respond204()));
                          }
                          else {
                            if (isSamePositionInQueueError(update)) {
                              asyncResultHandler.handle(succeededFuture(
                                PutRequestStorageRequestsByRequestIdResponse
                                  .respond422WithApplicationJson(samePositionInQueueError(entity))));
                            } else {
                              asyncResultHandler.handle(succeededFuture(
                                PutRequestStorageRequestsByRequestIdResponse
                                  .respond500WithTextPlain(reply.cause().toString())));
                            }
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(succeededFuture(
                            PutRequestStorageRequestsByRequestIdResponse
                              .respond500WithTextPlain(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(succeededFuture(
                      PutRequestStorageRequestsByRequestIdResponse
                        .respond500WithTextPlain(e.getMessage())));
                  }
                }
                else {
                  try {
                    postgresClient.save(REQUEST_TABLE, entity.getId(), entity,
                      save -> {
                        try {
                          if(save.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(succeededFuture(
                              PutRequestStorageRequestsByRequestIdResponse
                                .respond204()));
                          }
                          else {
                            if (isSamePositionInQueueError(save)) {
                              asyncResultHandler.handle(succeededFuture(
                                PutRequestStorageRequestsByRequestIdResponse
                                  .respond422WithApplicationJson(samePositionInQueueError(entity))));
                            } else {
                              asyncResultHandler.handle(succeededFuture(
                                PutRequestStorageRequestsByRequestIdResponse
                                  .respond500WithTextPlain(reply.cause().toString())));
                            }
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(succeededFuture(
                            PutRequestStorageRequestsByRequestIdResponse
                              .respond500WithTextPlain(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(succeededFuture(
                      PutRequestStorageRequestsByRequestIdResponse
                        .respond500WithTextPlain(e.getMessage())));
                  }
                }
              } else {
                asyncResultHandler.handle(succeededFuture(
                  PutRequestStorageRequestsByRequestIdResponse
                    .respond500WithTextPlain(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(succeededFuture(
            PutRequestStorageRequestsByRequestIdResponse
              .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(succeededFuture(
        PutRequestStorageRequestsByRequestIdResponse
          .respond500WithTextPlain(e.getMessage())));
    }
  }

  private Errors samePositionInQueueError(Request request) {
    Error error = new Error();

    error.withMessage("Cannot have more than one request with the same position in the queue")
      .withAdditionalProperty("itemId", request.getItemId())
      .withAdditionalProperty("position", request.getPosition());

    List<Error> errorList = new ArrayList<>();
    errorList.add(error);

    Errors errors = new Errors();
    errors.setErrors(errorList);

    return errors;
  }

  private <T> boolean isSamePositionInQueueError(AsyncResult<T> reply) {
    return reply.cause() instanceof GenericDatabaseException &&
      ((GenericDatabaseException) reply.cause()).errorMessage().message()
        .contains("request_itemid_position_idx_unique");
  }
}
