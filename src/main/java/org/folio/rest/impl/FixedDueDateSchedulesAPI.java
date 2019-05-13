package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FixedDueDateSchedule;
import org.folio.rest.jaxrs.model.FixedDueDateSchedules;
import org.folio.rest.jaxrs.model.Schedule;
import org.folio.rest.jaxrs.resource.FixedDueDateScheduleStorage;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class FixedDueDateSchedulesAPI implements FixedDueDateScheduleStorage {

  private static final Logger       log               = LoggerFactory.getLogger(FixedDueDateSchedulesAPI.class);
  private static final String       FIXED_SCHEDULE_TABLE  = "fixed_due_date_schedule";
  private static final String       INVALID_DATE_MSG  = "Unable to save fixed loan date. Date range not valid";

  private static final Class<FixedDueDateSchedule> DUE_DATE_SCHEDULE_CLASS = FixedDueDateSchedule.class;

  @Override
  @Validate
  public void deleteFixedDueDateScheduleStorageFixedDueDateSchedules(
      String lang,
      Map<String,
      String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
            TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(
            String.format("DELETE FROM %s_%s.%s", tenantId,
              PomReader.INSTANCE.getModuleName(), FIXED_SCHEDULE_TABLE), reply -> {
                if(reply.succeeded()){
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorage
                      .DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .noContent().build()));
                }
                else{
                  log.error(reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      FixedDueDateScheduleStorage.DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                          .respond500WithTextPlain(reply.cause().getMessage())));
                }
            });
      } catch (Exception e) {
        log.error(e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            FixedDueDateScheduleStorage.DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                .respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  @Override
  public void getFixedDueDateScheduleStorageFixedDueDateSchedules(
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
          PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
              TenantTool.calculateTenantId(tenantId));

          String[] fieldList = { "*" };

          CQLWrapper cql = getCQL(query, limit, offset);
          postgresClient.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, fieldList, cql, true, false, reply -> {
            try {
              if (reply.succeeded()) {
                @SuppressWarnings("unchecked")
                List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result().getResults();

                FixedDueDateSchedules pagedSchedules = new FixedDueDateSchedules();
                pagedSchedules.setFixedDueDateSchedules(dueDateSchedules);
                pagedSchedules.setTotalRecords((Integer) reply.result().getResultInfo().getTotalRecords());

                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .respond200WithApplicationJson(pagedSchedules)));
              } else {
                log.error(reply.cause());

                if(reply.cause() instanceof CQLQueryValidationException) {
                  CQLQueryValidationException exception = (CQLQueryValidationException)reply.cause();

                  String field = exception.getMessage();
                  int start = field.indexOf('\'');
                  int end = field.lastIndexOf('\'');
                  if(start != -1 && end != -1){
                    field = field.substring(start+1, end);
                  }
                  Errors e = ValidationHelper.createValidationErrorMessage(field, "", exception.getMessage());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                      .respond422WithApplicationJson(e)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                          .respond500WithTextPlain(reply.cause().getMessage())));
                }
              }
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                      .respond500WithTextPlain(e.getMessage())));
            }
          });
        }
        catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                  .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void postFixedDueDateScheduleStorageFixedDueDateSchedules(
      String lang,
      FixedDueDateSchedule entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {

      Errors errors = isDateRangeValid(entity.getSchedules());
      if(entity != null && errors != null){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .respond422WithApplicationJson(errors)));
        return;
      }

      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {
          if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
          }

          postgresClient.save(FIXED_SCHEDULE_TABLE, entity.getId(), entity, reply -> {
            try {
              if (reply.succeeded()) {
                OutStream stream = new OutStream();
                stream.setData(entity);

                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                      .respond201WithApplicationJson(entity, 
                        PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse.headersFor201().withLocation(reply.result()))));
              } else {
                log.error(reply.cause());
                if(isUniqueViolation(reply.cause())){
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .respond400WithTextPlain(PgExceptionUtil.badRequestMessage(reply.cause()))));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .respond500WithTextPlain(reply.cause().getMessage())));
                }
              }
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                    .respond500WithTextPlain(e.getMessage())));
            }
          });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                  .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void getFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleId(
      String fixedDueDateScheduleId,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

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
                @SuppressWarnings({ "unchecked" })
                List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result().getResults();

                if (dueDateSchedules.size() == 1) {
                  FixedDueDateSchedule dueDateSchedule = dueDateSchedules.get(0);

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .respond200WithApplicationJson(dueDateSchedule)));
                } else {
                  asyncResultHandler.handle(Future.succeededFuture(
                      FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .respond404WithTextPlain(PgExceptionUtil.badRequestMessage(reply.cause()))));
                }
              } else {
                log.error(reply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                    FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                        .respond500WithTextPlain(reply.cause().getMessage())));

              }
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .respond500WithTextPlain(e.getMessage())));
            }
          });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .respond500WithTextPlain(e.getMessage())));
    }
  }


  private Criterion getFixedDueDateScheduleId(String fixedDueDateScheduleId) {
    Criteria a = new Criteria();
    a.addField("'id'");
    a.setOperation("=");
    a.setValue(fixedDueDateScheduleId);
    return new Criterion(a);
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
      )  {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      Criterion criterion = getFixedDueDateScheduleId(fixedDueDateScheduleId);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete(FIXED_SCHEDULE_TABLE, criterion, reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .respond204()));
            } else {
              log.error(reply.cause());
              if(isBadId(reply.cause())){
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .respond404WithTextPlain(PgExceptionUtil.badRequestMessage(reply.cause()))));
              }
              else if(iStillReferenced(reply.cause())){
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .respond400WithTextPlain(PgExceptionUtil.badRequestMessage(reply.cause()))));
              }
              else{
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .respond500WithTextPlain(reply.cause().getMessage())));
              }
            }
          });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(Future
              .succeededFuture(DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(
          Future.succeededFuture(DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .respond500WithTextPlain(e.getMessage())));
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
      ) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {

      Errors errors = isDateRangeValid(entity.getSchedules());
      if(entity != null && errors != null){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .respond422WithApplicationJson(errors)));
        return;
      }

      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      Criterion criterion = getFixedDueDateScheduleId(fixedDueDateScheduleId);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, criterion, true, false, reply -> {
            if (reply.succeeded()) {
              @SuppressWarnings("unchecked")
              List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result().getResults();

              if (dueDateSchedules.size() == 1) {
                try {
                  postgresClient.update(FIXED_SCHEDULE_TABLE, entity, criterion, true, update -> {
                    try {
                      if (update.succeeded()) {
                        OutStream stream = new OutStream();
                        stream.setData(entity);

                        asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .respond204()));
                      } else {
                        log.error(update.cause());
                        if(isUniqueViolation(update.cause())){
                          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                            FixedDueDateScheduleStorage.
                              PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .respond400WithTextPlain(PgExceptionUtil.badRequestMessage(update.cause()))));
                        }
                        else{
                          log.error(update.cause());
                          asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .respond500WithTextPlain(update.cause().getMessage())));
                        }
                      }
                    } catch (Exception e) {
                      log.error(e);
                      asyncResultHandler.handle(Future.succeededFuture(
                          PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                              .respond500WithTextPlain(e.getMessage())));
                    }
                  });
                } catch (Exception e) {
                  log.error(e);
                  asyncResultHandler.handle(Future.succeededFuture(
                      PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .respond500WithTextPlain(e.getMessage())));
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
                                .respond204()));
                      } else {
                          log.error(save.cause());
                          asyncResultHandler.handle(Future.succeededFuture(
                              PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                  .respond500WithTextPlain(save.cause().getMessage())));
                      }
                    } catch (Exception e) {
                      log.error(e);
                      asyncResultHandler.handle(Future.succeededFuture(
                          PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                              .respond500WithTextPlain(e.getMessage())));
                    }
                  });
                } catch (Exception e) {
                    log.error(e);
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .respond500WithTextPlain(e.getMessage())));
                }
              }
            } else {
              log.error(reply.cause());
              if(isBadId(reply.cause())){
                asyncResultHandler.handle(Future
                  .succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .respond404WithTextPlain(PgExceptionUtil.badRequestMessage(reply.cause()))));
              }
              else{
                asyncResultHandler.handle(Future
                  .succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .respond500WithTextPlain(reply.cause().getMessage())));
              }
            }
          });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(
              Future.succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(
          Future.succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .respond500WithTextPlain(e.getMessage())));
    }
  }

  private CQLWrapper getCQL(String query, int limit, int offset)
    throws FieldException {

    CQL2PgJSON cql2pgJson = new CQL2PgJSON("fixed_due_date_schedule.jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }



  private Errors isDateRangeValid(List<Schedule> schedules) {

    Errors errors = null;
    try {
      //String from, String to, String due
      int size = schedules.size();
      for (int i = 0; i < size; i++) {
        Date dueDate = schedules.get(i).getDue();
        Date fromDate = schedules.get(i).getFrom();
        Date toDate = schedules.get(i).getTo();

        if(fromDate.compareTo(dueDate) > 0){
          errors = ValidationHelper.createValidationErrorMessage("from", fromDate.toString(),
            INVALID_DATE_MSG + " from date after due date");
        }
        else if(fromDate.compareTo(toDate) > 0){
          errors = ValidationHelper.createValidationErrorMessage("from", fromDate.toString(),
            INVALID_DATE_MSG + " from date after to date");
        }
        else if(toDate.compareTo(dueDate) > 0){
          errors = ValidationHelper.createValidationErrorMessage("to", toDate.toString(),
            INVALID_DATE_MSG + " to date after due date");
        }
        if(errors != null){
          log.info(
            INVALID_DATE_MSG + schedules.get(i).getDue()
            + " , from date: " + schedules.get(i).getFrom()
            + " , to date: " + schedules.get(i).getTo());
        }
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return errors;
  }

  private boolean isUniqueViolation(Throwable e){
    return e != null
      && e.getMessage().contains("duplicate key value violates unique constraint");
  }

  private boolean isBadId(Throwable e){
    return e != null
      && e.getMessage().contains("invalid input syntax for type numeric");
  }

  private boolean iStillReferenced(Throwable e){
    return e != null
      && e.getMessage().contains("violates foreign key constraint");
  }
}
