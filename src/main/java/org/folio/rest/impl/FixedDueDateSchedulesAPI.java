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
import org.folio.rest.persist.MyPgUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.support.UUIDValidation;

import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

        postgresClient.execute(
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
    PgUtil.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, FixedDueDateSchedules.class,
        query, offset, limit, okapiHeaders, vertxContext,
        FixedDueDateScheduleStorage.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void postFixedDueDateScheduleStorageFixedDueDateSchedules(
      String lang,
      FixedDueDateSchedule entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    Errors errors = isDateRangeValid(entity.getSchedules());
    if (errors != null){
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
            .respond422WithApplicationJson(errors)));
      return;
    }

    PgUtil.post(FIXED_SCHEDULE_TABLE, entity, okapiHeaders, vertxContext,
        FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void getFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleId(
      String fixedDueDateScheduleId,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    // TODO: do we really need this special check?
    // A 404 "Not found" from PgUtil.getById without "invalid UUID format" is good enough, isn't it?
    if (! UUIDValidation.isValidUUID(fixedDueDateScheduleId)) {
      asyncResultHandler.handle(Future.succeededFuture(
          GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
          .respond404WithTextPlain("Not found, invalid UUID format")));
      return;
    }
    PgUtil.getById(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, fixedDueDateScheduleId, okapiHeaders, vertxContext,
        GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse.class,
        asyncResultHandler);
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
    PgUtil.deleteById(FIXED_SCHEDULE_TABLE, fixedDueDateScheduleId, okapiHeaders, vertxContext,
        DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse.class,
        asyncResultHandler);
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

    Errors errors = isDateRangeValid(entity.getSchedules());
    if (errors != null){
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
            .respond422WithApplicationJson(errors)));
      return;
    }

    MyPgUtil.putUpsert204(FIXED_SCHEDULE_TABLE, entity, fixedDueDateScheduleId, okapiHeaders, vertxContext,
        PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse.class, asyncResultHandler);
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
}
