package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import org.folio.rest.impl.util.RequestsApiUtil;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Requests;
import org.folio.rest.jaxrs.resource.RequestStorage;
import org.folio.rest.persist.MyPgUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.RTFConsts;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.Map;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class RequestsAPI implements RequestStorage {
  public static final String REQUEST_TABLE = "request";

  @Override
  public void deleteRequestStorageRequests(
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("TRUNCATE TABLE %s_%s.%s",
          tenantId, "mod_circulation_storage", REQUEST_TABLE),
          reply -> asyncResultHandler.handle(succeededFuture(
            DeleteRequestStorageRequestsResponse.respond204())));
      }
      catch(Exception e) {
        asyncResultHandler.handle(succeededFuture(
          DeleteRequestStorageRequestsResponse
            .respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  @Override
  public void getRequestStorageRequests(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.get(REQUEST_TABLE, Request.class, Requests.class, query, offset, limit, okapiHeaders, vertxContext,
        GetRequestStorageRequestsResponse.class, asyncResultHandler);
  }

  @Override
  public void postRequestStorageRequests(
    String lang,
    Request entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.post(REQUEST_TABLE, entity, okapiHeaders, vertxContext,
        PostRequestStorageRequestsResponse.class, reply -> {
          if (isSamePositionInQueueError(reply)) {
            asyncResultHandler.handle(succeededFuture(
              PostRequestStorageRequestsResponse
                .respond422WithApplicationJson(samePositionInQueueError(entity))));
            return;
          }
          asyncResultHandler.handle(reply);
        });
  }

  @Override
  public void getRequestStorageRequestsByRequestId(
    String requestId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(REQUEST_TABLE, Request.class, requestId, okapiHeaders, vertxContext,
        GetRequestStorageRequestsByRequestIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteRequestStorageRequestsByRequestId(
    String requestId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(REQUEST_TABLE, requestId, okapiHeaders, vertxContext,
        DeleteRequestStorageRequestsByRequestIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putRequestStorageRequestsByRequestId(
    String requestId,
    String lang, Request entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    // TODO: On insert don't return 204, we must return 201!
    MyPgUtil.putUpsert204(REQUEST_TABLE, entity, requestId, okapiHeaders, vertxContext,
        PutRequestStorageRequestsByRequestIdResponse.class, reply -> {
          if (isSamePositionInQueueError(reply)) {
            asyncResultHandler.handle(succeededFuture(
              PutRequestStorageRequestsByRequestIdResponse
                .respond422WithApplicationJson(samePositionInQueueError(entity))));
            return;
          }
          asyncResultHandler.handle(reply);
        });
  }

  private Errors samePositionInQueueError(Request request) {
    return RequestsApiUtil
      .samePositionInQueueError(request.getItemId(), request.getPosition());
  }

  private boolean isSamePositionInQueueError(AsyncResult<Response> reply) {
    String message = null;

    if (reply.succeeded() && reply.result().getStatus() >= 400 &&
      reply.result().getStatus() < 600 && reply.result().hasEntity()) {

      // When entity is an instance of Errors, the getEntity().toString()
      // will return an object identifier. Process the object to correctly
      // parse the message.
      if (reply.result().getEntity() instanceof Errors) {
        Errors errors = (Errors) reply.result().getEntity();

        for (int i = 0; i < errors.getErrors().size(); i++) {
          Error error = errors.getErrors().get(i);

          if (error.getType() == RTFConsts.VALIDATION_FIELD_ERROR)
            message = error.getMessage().toLowerCase();
        }
      } else {
        message = reply.result().getEntity().toString().toLowerCase();
      }
    }

    return RequestsApiUtil.hasSamePositionConstraintViolated(message);
  }
}
