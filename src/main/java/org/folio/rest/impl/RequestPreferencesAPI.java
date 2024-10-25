package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.impl.util.OkapiResponseUtil;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.RequestPreference;
import org.folio.rest.jaxrs.model.RequestPreferences;
import org.folio.rest.jaxrs.resource.RequestPreferenceStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.utils.ValidationHelper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class RequestPreferencesAPI implements RequestPreferenceStorage {

  private static final String REQUEST_PREFERENCE_TABLE = "user_request_preference";

  @Override
  @Validate
  public void getRequestPreferenceStorageRequestPreference(String totalRecords, int offset,
    int limit, String query, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.get(REQUEST_PREFERENCE_TABLE, RequestPreference.class, RequestPreferences.class,
      query, offset, limit, okapiHeaders, vertxContext,
      GetRequestPreferenceStorageRequestPreferenceResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postRequestPreferenceStorageRequestPreference(RequestPreference entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.post(REQUEST_PREFERENCE_TABLE, entity, okapiHeaders, vertxContext, PostRequestPreferenceStorageRequestPreferenceResponse.class, uniqueUserViolationHandler(entity, asyncResultHandler));
  }

  @Override
  @Validate
  public void getRequestPreferenceStorageRequestPreferenceById(String id,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(REQUEST_PREFERENCE_TABLE, RequestPreference.class, id, okapiHeaders, vertxContext,
      GetRequestPreferenceStorageRequestPreferenceByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteRequestPreferenceStorageRequestPreferenceById(String id,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(REQUEST_PREFERENCE_TABLE, id, okapiHeaders, vertxContext,
      DeleteRequestPreferenceStorageRequestPreferenceByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putRequestPreferenceStorageRequestPreferenceById(String id, RequestPreference entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.put(REQUEST_PREFERENCE_TABLE, entity, id, okapiHeaders, vertxContext, PutRequestPreferenceStorageRequestPreferenceByIdResponse.class, uniqueUserViolationHandler(entity, asyncResultHandler));
  }

  private Handler<AsyncResult<Response>> uniqueUserViolationHandler(RequestPreference entity, Handler<AsyncResult<Response>> asyncResultHandler) {
    return reply -> {
      if (isUniqueUserIdViolation(reply)) {
        asyncResultHandler.handle(
          succeededFuture(Response.status(HTTP_UNPROCESSABLE_ENTITY.toInt())
            .header("Content-Type", "application/json")
            .entity(preferenceAlreadyExistsError(entity.getUserId())).build()));
      } else {
        asyncResultHandler.handle(reply);
      }
    };
  }

  private boolean isUniqueUserIdViolation(AsyncResult<Response> reply) {
    return OkapiResponseUtil.containsErrorMessage(
      reply, " value already exists in table ");
  }

  private Errors preferenceAlreadyExistsError(String userId) {
    return ValidationHelper.createValidationErrorMessage(
      "userId", userId,
      "Request preference for specified user already exists");
  }
}
