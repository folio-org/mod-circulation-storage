package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FixedDueDateSchedule;
import org.folio.rest.jaxrs.model.FixedDueDateSchedules;
import org.folio.rest.jaxrs.resource.FixedDueDateScheduleStorageResource;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class FixedDueDateSchedulesAPI implements FixedDueDateScheduleStorageResource {

  private final String FIXED_SCHEDULE_TABLE = "fixed-due-date-schedule";
  private final Class<FixedDueDateSchedule> DUE_DATE_SCHEDULE_CLASS = FixedDueDateSchedule.class;

  @Override
  @Validate
  public void deleteFixedDueDateScheduleStorageFixedDueDateSchedules(
      String lang,
      Map<String,
      String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
            TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(
            String.format("TRUNCATE TABLE %s_%s.%s", tenantId, "circulation_storage", FIXED_SCHEDULE_TABLE), reply -> {
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorageResource.DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                      .noContent().build()));
            });
      } catch (Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            FixedDueDateScheduleStorageResource.DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                .withPlainInternalServerError(e.getMessage())));
      }
    });
  }

  @Override
  public void getFixedDueDateScheduleStorageFixedDueDateSchedules(
      int offset,
      int limit,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
              TenantTool.calculateTenantId(tenantId));

          String[] fieldList = { "*" };

          CQL2PgJSON cql2pgJson = new CQL2PgJSON("fixed_due_date_schedule.jsonb");
          CQLWrapper cql = new CQLWrapper(cql2pgJson, null).setLimit(new Limit(limit)).setOffset(new Offset(offset));

          postgresClient.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, fieldList, cql, true, false, reply -> {
            try {
              if (reply.succeeded()) {
                List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result()[0];

                FixedDueDateSchedules pagedSchedules = new FixedDueDateSchedules();
                pagedSchedules.setFixedDueDateSchedules(dueDateSchedules);
                pagedSchedules.setTotalRecords((Integer) reply.result()[1]);

                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withJsonOK(pagedSchedules)));
              } else {
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withPlainInternalServerError(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              e.printStackTrace();
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                      .withPlainInternalServerError(e.getMessage())));
            }
          });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void postFixedDueDateScheduleStorageFixedDueDateSchedules(
      String lang,
      FixedDueDateSchedule entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {
          if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
          }

          postgresClient.save("fixed_due_date_schedule", entity.getId(), entity, reply -> {
            try {
              if (reply.succeeded()) {
                OutStream stream = new OutStream();
                stream.setData(entity);

                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withJsonCreated(reply.result(), stream)));
              } else {
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withPlainInternalServerError(reply.cause().toString())));
              }
            } catch (Exception e) {
              e.printStackTrace();
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                      .withPlainInternalServerError(e.getMessage())));
            }
          });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void getFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleId(
      String fixedDueDateScheduleId,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(fixedDueDateScheduleId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, criterion, true, false, reply -> {
            try {
              if (reply.succeeded()) {
                List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result()[0];

                if (dueDateSchedules.size() == 1) {
                  FixedDueDateSchedule dueDateSchedule = dueDateSchedules.get(0);

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .withJsonOK(dueDateSchedule)));
                } else {
                  asyncResultHandler.handle(Future.succeededFuture(
                      FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .withPlainNotFound("Not Found")));
                }
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                        .withPlainInternalServerError(reply.cause().getMessage())));

              }
            } catch (Exception e) {
              e.printStackTrace();
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withPlainInternalServerError(e.getMessage())));
            }
          });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void deleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleId(
      String fixedDueDateScheduleId,
      String lang,
      Map<String,
      String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(fixedDueDateScheduleId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete(FIXED_SCHEDULE_TABLE, criterion, reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withNoContent()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withPlainInternalServerError(reply.cause().getMessage())));
            }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future
              .succeededFuture(DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(
          Future.succeededFuture(DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void putFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleId(
      String fixedDueDateScheduleId,
      String lang,
      FixedDueDateSchedule entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(fixedDueDateScheduleId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, criterion, true, false, reply -> {
            if (reply.succeeded()) {
              List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result()[0];

              if (dueDateSchedules.size() == 1) {
                try {
                  postgresClient.update(FIXED_SCHEDULE_TABLE, entity, criterion, true, update -> {
                    try {
                      if (update.succeeded()) {
                        OutStream stream = new OutStream();
                        stream.setData(entity);

                        asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .withNoContent()));
                      } else {
                        asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .withPlainInternalServerError(update.cause().getMessage())));
                      }
                    } catch (Exception e) {
                      asyncResultHandler.handle(Future.succeededFuture(
                          PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                              .withPlainInternalServerError(e.getMessage())));
                    }
                  });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                      PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .withPlainInternalServerError(e.getMessage())));
                }
              } else {
                try {
                  postgresClient.save(FIXED_SCHEDULE_TABLE, entity.getId(), entity, save -> {
                    try {
                      if (save.succeeded()) {
                        OutStream stream = new OutStream();
                        stream.setData(entity);

                        asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .withNoContent()));
                      } else {
                        asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .withPlainInternalServerError(save.cause().getMessage())));
                      }
                    } catch (Exception e) {
                      asyncResultHandler.handle(Future.succeededFuture(
                          PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                              .withPlainInternalServerError(e.getMessage())));
                    }
                  });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                      PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .withPlainInternalServerError(e.getMessage())));
                }
              }
            } else {
              asyncResultHandler.handle(Future
                  .succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withPlainInternalServerError(reply.cause().getMessage())));
            }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(
              Future.succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(
          Future.succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }
}
