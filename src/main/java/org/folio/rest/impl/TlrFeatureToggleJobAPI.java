package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.persist.TlrFeatureToggleJob;
import org.folio.rest.jaxrs.resource.TlrToggleJobStorage;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class TlrFeatureToggleJobAPI implements TlrToggleJobStorage {

  private static final String TOGGLE_JOB_TABLE = "toggle_job";

  @Override
  public void postTlrToggleJobStorage(org.folio.rest.jaxrs.model.TlrFeatureToggleJob entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.post(TOGGLE_JOB_TABLE, entity, okapiHeaders, vertxContext,
      TlrToggleJobStorage.PostTlrToggleJobStorageResponse.class, asyncResultHandler);
  }

  @Override
  public void getTlrToggleJobStorageByToggleJobId(String toggleJobId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

      PgUtil.getById(TOGGLE_JOB_TABLE, TlrFeatureToggleJob.class, toggleJobId, okapiHeaders,
        vertxContext, TlrToggleJobStorage.GetTlrToggleJobStorageByToggleJobIdResponse.class,
        asyncResultHandler);
  }
}
