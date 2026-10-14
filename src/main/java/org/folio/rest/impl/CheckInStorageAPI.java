package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CheckIn;
import org.folio.rest.jaxrs.resource.CheckInStorageCheckIns;
import org.folio.service.checkin.CheckInService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class CheckInStorageAPI implements CheckInStorageCheckIns {

  @Validate
  @Override
  public void getCheckInStorageCheckIns(String totalRecords, int offset, int limit, String query,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (offset < 0) {
      asyncResultHandler.handle(Future.succeededFuture(
        GetCheckInStorageCheckInsResponse.respond400WithTextPlain(
          "'offset' parameter must be greater than or equal to 0")));
      return;
    }
    if (limit < 0) {
      asyncResultHandler.handle(Future.succeededFuture(
        GetCheckInStorageCheckInsResponse.respond400WithTextPlain(
          "'limit' parameter must be greater than or equal to 0")));
      return;
    }

    new CheckInService(vertxContext, okapiHeaders).findByQuery(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postCheckInStorageCheckIns(CheckIn entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new CheckInService(vertxContext, okapiHeaders).create(entity)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getCheckInStorageCheckInsByCheckInId(String checkInId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new CheckInService(vertxContext, okapiHeaders).findById(checkInId)
        .onComplete(asyncResultHandler);
  }

}
