package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.resource.RequestStorage;
import org.folio.service.request.RequestService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class RequestsAPI implements RequestStorage {

  @Validate
  @Override
  public void deleteRequestStorageRequests(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).deleteAll()
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getRequestStorageRequests(String totalRecords, int offset, int limit, String query,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).findByQuery(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postRequestStorageRequests(Request request, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).create(request)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getRequestStorageRequestsByRequestId(String requestId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).findById(requestId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteRequestStorageRequestsByRequestId(String requestId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).delete(requestId)
        .onComplete(asyncResultHandler);
  }
  @Validate
  @Override
  public void getRequestStorageAnonymizationSettings(
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new org.folio.service.request.RequestService(vertxContext, okapiHeaders)
      .getAnonymizationSettings()
      .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postRequestStorageAnonymizationSettings(
    org.folio.rest.jaxrs.model.AnonymizationSettings entity,
    Map<String,String> okapiHeaders,
    Handler<AsyncResult<Response>> h,
    Context ctx) {
    new org.folio.service.request.RequestService(ctx, okapiHeaders)
      .createAnonymizationSettings(entity)
      .onComplete(h);
  }

  @Validate
  @Override
  public void putRequestStorageRequestsByRequestId(String requestId, Request request,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).createOrUpdate(requestId, request)
        .onComplete(asyncResultHandler);
  }

}
