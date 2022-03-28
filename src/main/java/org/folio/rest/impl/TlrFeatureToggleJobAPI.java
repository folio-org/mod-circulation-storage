package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.jaxrs.resource.TlrToggleJobStorage;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class TlrFeatureToggleJobAPI implements TlrToggleJobStorage {

  private static final String TLR_FEATURE_TOGGLE_JOB_TABLE = "tlr_feature_toggle_job";

  @Override
  public void postTlrToggleJobStorage(TlrFeatureToggleJob entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.post(TLR_FEATURE_TOGGLE_JOB_TABLE, entity, okapiHeaders, vertxContext,
      PostTlrToggleJobStorageResponse.class, asyncResultHandler);
  }

  @Override
  public void getTlrToggleJobStorageByToggleJobId(String toggleJobId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(TLR_FEATURE_TOGGLE_JOB_TABLE, TlrFeatureToggleJob.class, toggleJobId,
      okapiHeaders, vertxContext, GetTlrToggleJobStorageByToggleJobIdResponse.class,
      asyncResultHandler);
  }
}
