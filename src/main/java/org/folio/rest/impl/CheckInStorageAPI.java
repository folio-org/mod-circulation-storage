package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CheckIn;
import org.folio.rest.jaxrs.model.CheckIns;
import org.folio.rest.jaxrs.resource.CheckInStorageCheckIns;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class CheckInStorageAPI implements CheckInStorageCheckIns {
  private static final String TABLE_NAME = "check_in";

  @Validate
  @Override
  public void getCheckInStorageCheckIns(
    int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.get(TABLE_NAME, CheckIn.class, CheckIns.class, query, offset,
      limit, okapiHeaders, vertxContext, GetCheckInStorageCheckInsResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postCheckInStorageCheckIns(
    String lang, CheckIn entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.post(TABLE_NAME, entity, okapiHeaders, vertxContext,
      PostCheckInStorageCheckInsResponse.class, asyncResultHandler);

  }

  @Validate
  @Override
  public void getCheckInStorageCheckInsByCheckInId(
    String checkInId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(TABLE_NAME, CheckIn.class, checkInId,
      okapiHeaders, vertxContext, GetCheckInStorageCheckInsByCheckInIdResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteCheckInStorageCheckInsByCheckInId(
    String checkInId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.deleteById(TABLE_NAME, checkInId, okapiHeaders, vertxContext,
      DeleteCheckInStorageCheckInsByCheckInIdResponse.class, asyncResultHandler);
  }
}
