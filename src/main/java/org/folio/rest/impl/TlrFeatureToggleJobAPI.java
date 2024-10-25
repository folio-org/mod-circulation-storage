package org.folio.rest.impl;

import static org.folio.support.ModuleConstants.TLR_FEATURE_TOGGLE_JOB_CLASS;
import static org.folio.support.ModuleConstants.TLR_FEATURE_TOGGLE_JOB_TABLE;

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

  @Override
  public void getTlrFeatureToggleJobStorageTlrFeatureToggleJobs(String totalRecords, int offset,
    int limit, String query, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.get(TLR_FEATURE_TOGGLE_JOB_TABLE, TLR_FEATURE_TOGGLE_JOB_CLASS, TlrFeatureToggleJobs.class,
      query, offset, limit, okapiHeaders, vertxContext,
      GetTlrFeatureToggleJobStorageTlrFeatureToggleJobsResponse.class, asyncResultHandler);
  }

  @Override
  public void postTlrFeatureToggleJobStorageTlrFeatureToggleJobs(TlrFeatureToggleJob entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.post(TLR_FEATURE_TOGGLE_JOB_TABLE, entity, okapiHeaders, vertxContext,
      PostTlrFeatureToggleJobStorageTlrFeatureToggleJobsResponse.class, asyncResultHandler);
  }

  @Override
  public void getTlrFeatureToggleJobStorageTlrFeatureToggleJobsById(String id,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(TLR_FEATURE_TOGGLE_JOB_TABLE, TLR_FEATURE_TOGGLE_JOB_CLASS, id,
      okapiHeaders, vertxContext,
      GetTlrFeatureToggleJobStorageTlrFeatureToggleJobsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putTlrFeatureToggleJobStorageTlrFeatureToggleJobsById(String id,
    TlrFeatureToggleJob entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.put(TLR_FEATURE_TOGGLE_JOB_TABLE, entity, id, okapiHeaders,
      vertxContext, PutTlrFeatureToggleJobStorageTlrFeatureToggleJobsByIdResponse.class,
      asyncResultHandler);
  }

  @Override
  public void deleteTlrFeatureToggleJobStorageTlrFeatureToggleJobsById(String id,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(TLR_FEATURE_TOGGLE_JOB_TABLE, id, okapiHeaders, vertxContext,
      DeleteTlrFeatureToggleJobStorageTlrFeatureToggleJobsByIdResponse.class, asyncResultHandler);
  }
}
