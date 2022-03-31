package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJobs;
import org.folio.rest.jaxrs.resource.TlrFeatureToggleJobStorage;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class TlrFeatureToggleJobAPI implements TlrFeatureToggleJobStorage {

  private static final String TLR_FEATURE_TOGGLE_JOB_TABLE = "tlr_feature_toggle_job";

  @Override
  public void getTlrFeatureToggleJobStorageTlrFeatureToggleJobs(int offset, int limit,
    String query, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.get(TLR_FEATURE_TOGGLE_JOB_TABLE, TlrFeatureToggleJob.class, TlrFeatureToggleJobs.class,
      query, offset, limit, okapiHeaders, vertxContext,
      GetTlrFeatureToggleJobStorageTlrFeatureToggleJobsResponse.class, asyncResultHandler);
  }

  @Override
  public void postTlrFeatureToggleJobStorageTlrFeatureToggleJobs(String lang,
    TlrFeatureToggleJob entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.post(TLR_FEATURE_TOGGLE_JOB_TABLE, entity, okapiHeaders, vertxContext,
      PostTlrFeatureToggleJobStorageTlrFeatureToggleJobsResponse.class, asyncResultHandler);
  }

  @Override
  public void getTlrFeatureToggleJobStorageTlrFeatureToggleJobsById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(TLR_FEATURE_TOGGLE_JOB_TABLE, TlrFeatureToggleJob.class, id,
      okapiHeaders, vertxContext,
      GetTlrFeatureToggleJobStorageTlrFeatureToggleJobsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putTlrFeatureToggleJobStorageTlrFeatureToggleJobsById(String id, String lang,
    TlrFeatureToggleJob entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.put(TLR_FEATURE_TOGGLE_JOB_TABLE, entity, id, okapiHeaders,
      vertxContext, PutTlrFeatureToggleJobStorageTlrFeatureToggleJobsByIdResponse.class,
      asyncResultHandler);
  }

  @Override
  public void deleteTlrFeatureToggleJobStorageTlrFeatureToggleJobsById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(TLR_FEATURE_TOGGLE_JOB_TABLE, id, okapiHeaders, vertxContext,
      DeleteTlrFeatureToggleJobStorageTlrFeatureToggleJobsByIdResponse.class, asyncResultHandler);
  }
}
