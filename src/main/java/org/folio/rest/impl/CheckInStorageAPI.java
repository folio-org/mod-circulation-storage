package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CheckIn;
import org.folio.rest.jaxrs.resource.CheckInStorageCheckIns;
import org.folio.service.checkin.CheckInService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class CheckInStorageAPI implements CheckInStorageCheckIns {

  @Validate
  @Override
  public void getCheckInStorageCheckIns(
    int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new CheckInService(vertxContext, okapiHeaders).findByQuery(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postCheckInStorageCheckIns(
    String lang, CheckIn entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new CheckInService(vertxContext, okapiHeaders).create(entity)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getCheckInStorageCheckInsByCheckInId(
    String checkInId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new CheckInService(vertxContext, okapiHeaders).findById(checkInId)
        .onComplete(asyncResultHandler);
  }

}
