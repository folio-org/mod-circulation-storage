package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CheckInOperation;
import org.folio.rest.jaxrs.model.CheckInOperations;
import org.folio.rest.jaxrs.resource.CheckInOperationStorage;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class CheckInOperationAPI implements CheckInOperationStorage {
  private static final String TABLE_NAME = "check_in_operation";

  @Validate
  @Override
  public void getCheckInOperationStorage(
    int offset, int limit, String query, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.get(TABLE_NAME, CheckInOperation.class, CheckInOperations.class, query, offset,
      limit, okapiHeaders, vertxContext, GetCheckInOperationStorageResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postCheckInOperationStorage(
    String lang, CheckInOperation entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.post(TABLE_NAME, entity, okapiHeaders, vertxContext,
      PostCheckInOperationStorageResponse.class, asyncResultHandler);
  }

  @Override
  public void getCheckInOperationStorageByCheckInOperationId(
    String checkInOperationId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(TABLE_NAME, CheckInOperation.class, checkInOperationId,
      okapiHeaders, vertxContext, GetCheckInOperationStorageByCheckInOperationIdResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteCheckInOperationStorageByCheckInOperationId(
    String checkInOperationId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.deleteById(TABLE_NAME, checkInOperationId, okapiHeaders, vertxContext,
      DeleteCheckInOperationStorageByCheckInOperationIdResponse.class, asyncResultHandler);
  }
}
