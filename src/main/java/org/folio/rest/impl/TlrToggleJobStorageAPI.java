package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.ToggleJob;
import org.folio.rest.jaxrs.resource.TlrToggleJobStorage;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class TlrToggleJobStorageAPI implements TlrToggleJobStorage {

  private static final String TOGGLE_JOB_TABLE = "toggle_job";

  @Override
  public void postTlrToggleJobStorage(ToggleJob entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {


  }

  @Override
  public void getTlrToggleJobStorageByToggleJobId(String toggleJobId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

      PgUtil.getById(TOGGLE_JOB_TABLE, ToggleJob.class, toggleJobId, okapiHeaders, vertxContext,
        TlrToggleJobStorage.GetTlrToggleJobStorageByToggleJobIdResponse.class, asyncResultHandler);
  }
}
