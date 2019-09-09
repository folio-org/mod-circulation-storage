package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.RequestPreference;
import org.folio.rest.jaxrs.model.RequestPreferences;
import org.folio.rest.jaxrs.resource.RequestPreferenceStorage;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class RequestPreferencesImpl implements RequestPreferenceStorage {

  private static final String REQUEST_PREFERENCE_TABLE = "user_request_preference";

  @Override
  public void getRequestPreferenceStorageRequestPreference(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(REQUEST_PREFERENCE_TABLE, RequestPreference.class, RequestPreferences.class,
      query, offset, limit, okapiHeaders, vertxContext,
      GetRequestPreferenceStorageRequestPreferenceResponse.class, asyncResultHandler);
  }

  @Override
  public void postRequestPreferenceStorageRequestPreference(String lang, RequestPreference entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(REQUEST_PREFERENCE_TABLE, entity, okapiHeaders, vertxContext, PostRequestPreferenceStorageRequestPreferenceResponse.class, asyncResultHandler);
  }

  @Override
  public void getRequestPreferenceStorageRequestPreferenceById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REQUEST_PREFERENCE_TABLE, RequestPreference.class, id, okapiHeaders, vertxContext,
      GetRequestPreferenceStorageRequestPreferenceByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteRequestPreferenceStorageRequestPreferenceById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(REQUEST_PREFERENCE_TABLE, id, okapiHeaders, vertxContext,
      DeleteRequestPreferenceStorageRequestPreferenceByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putRequestPreferenceStorageRequestPreferenceById(String id, String lang, RequestPreference entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(REQUEST_PREFERENCE_TABLE, entity, id, okapiHeaders, vertxContext, PutRequestPreferenceStorageRequestPreferenceByIdResponse.class, asyncResultHandler);
  }
}
