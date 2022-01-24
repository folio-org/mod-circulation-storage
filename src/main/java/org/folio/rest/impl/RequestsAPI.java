package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.resource.RequestStorage;
import org.folio.service.request.RequestService;

public class RequestsAPI implements RequestStorage {

  @Validate
  @Override
  public void deleteRequestStorageRequests(
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).deleteAll()
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getRequestStorageRequests(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).findByQuery(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postRequestStorageRequests(
    String lang,
    Request request,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).create(request)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getRequestStorageRequestsByRequestId(
    String requestId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).findById(requestId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteRequestStorageRequestsByRequestId(
    String requestId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).delete(requestId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void putRequestStorageRequestsByRequestId(
    String requestId,
    String lang, Request request,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new RequestService(vertxContext, okapiHeaders).update(requestId, request)
        .onComplete(asyncResultHandler);
  }

}
