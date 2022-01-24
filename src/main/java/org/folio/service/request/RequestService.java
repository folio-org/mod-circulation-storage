package org.folio.service.request;

import static io.vertx.core.Promise.promise;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.service.event.EntityChangedEventPublisherFactory.requestEventPublisher;
import static org.folio.support.ModuleConstants.MODULE_NAME;
import static org.folio.support.ModuleConstants.REQUEST_CLASS;
import static org.folio.support.ModuleConstants.REQUEST_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import org.folio.persist.RequestRepository;
import org.folio.rest.impl.util.OkapiResponseUtil;
import org.folio.rest.impl.util.RequestsApiUtil;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Requests;
import org.folio.rest.jaxrs.resource.RequestStorage;
import org.folio.rest.persist.MyPgUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.event.EntityChangedEventPublisher;

public class RequestService {

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final RequestRepository repository;
  private final EntityChangedEventPublisher<String, Request> eventPublisher;


  public RequestService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    this.postgresClient = postgresClient(vertxContext, okapiHeaders);
    this.repository = new RequestRepository(vertxContext, okapiHeaders);
    this.eventPublisher = requestEventPublisher(vertxContext, okapiHeaders);
  }

  public Future<Response> findByQuery(String query, int offset, int limit) {
    return PgUtil.get(REQUEST_TABLE, REQUEST_CLASS, Requests.class, query, offset, limit, okapiHeaders, vertxContext,
        RequestStorage.GetRequestStorageRequestsResponse.class);
  }

  public Future<Response> findById(String requestId) {
    return PgUtil.getById(REQUEST_TABLE, REQUEST_CLASS, requestId, okapiHeaders, vertxContext,
        RequestStorage.GetRequestStorageRequestsByRequestIdResponse.class);
  }

  public Future<Response> create(Request request) {
    Promise<Response> createResult = Promise.promise();

    PgUtil.post(REQUEST_TABLE, request, okapiHeaders, vertxContext,
        RequestStorage.PostRequestStorageRequestsResponse.class, reply -> {
          if (isSamePositionInQueueError(reply)) {
            createResult.complete(RequestStorage.PostRequestStorageRequestsResponse
                .respond422WithApplicationJson(samePositionInQueueError(request)));
          } else {
            createResult.handle(reply);
          }
        });

    return createResult.future()
        .compose(eventPublisher.publishCreated());
  }

  public Future<Response> update(String requestId, Request request) {
    return repository.getById(requestId)
        .compose(oldRequest -> {
          Promise<Response> putResult = Promise.promise();

          // TODO: On insert don't return 204, we must return 201!
          MyPgUtil.putUpsert204(REQUEST_TABLE, request, requestId, okapiHeaders, vertxContext,
              RequestStorage.PutRequestStorageRequestsByRequestIdResponse.class, reply -> {
                if (isSamePositionInQueueErrorOnUpsert(reply)) {
                  putResult.complete(RequestStorage.PutRequestStorageRequestsByRequestIdResponse
                      .respond422WithApplicationJson(samePositionInQueueError(request)));
                } else {
                  putResult.handle(reply);
                }
              });

          return putResult.future()
              .compose(eventPublisher.publishUpdated(oldRequest));
        });
  }

  public Future<Response> delete(String requestId) {
    return repository.getById(requestId)
        .compose(request -> {
          final Promise<Response> deleteResult = promise();

          PgUtil.deleteById(REQUEST_TABLE, requestId, okapiHeaders, vertxContext,
              RequestStorage.DeleteRequestStorageRequestsByRequestIdResponse.class, deleteResult);

          return deleteResult.future()
              .compose(eventPublisher.publishRemoved(request));
        });
  }

  public Future<Response> deleteAll() {
    Promise<Response> deleteAllResult = Promise.promise();

    try {
      postgresClient.execute(String.format("TRUNCATE TABLE %s_%s.%s", tenantId(okapiHeaders), MODULE_NAME, REQUEST_TABLE),
          reply -> deleteAllResult.complete(reply.succeeded()
              ? RequestStorage.DeleteRequestStorageRequestsResponse.respond204()
              : RequestStorage.DeleteRequestStorageRequestsResponse.respond500WithTextPlain(reply.cause().getMessage())));
    }
    catch(Exception e) {
      deleteAllResult.complete(RequestStorage.DeleteRequestStorageRequestsResponse
          .respond500WithTextPlain(e.getMessage()));
    }

    return deleteAllResult.future()
        .compose(eventPublisher.publishAllRemoved());
  }

  private boolean isSamePositionInQueueError(AsyncResult<Response> reply) {
    String message = OkapiResponseUtil.getErrorMessage(reply);
    return RequestsApiUtil.hasSamePositionConstraintViolated(message);
  }

  private Errors samePositionInQueueError(Request request) {
    return RequestsApiUtil
        .samePositionInQueueError(request.getItemId(), request.getPosition());
  }

  // Remove/Replace this function when MyPgUtil.putUpsert204() is removed/replaced.
  private boolean isSamePositionInQueueErrorOnUpsert(AsyncResult<Response> reply) {
    return reply.succeeded()
        && reply.result().getStatus() == HTTP_BAD_REQUEST.toInt()
        && reply.result().hasEntity()
        && RequestsApiUtil
        .hasSamePositionConstraintViolated(reply.result().getEntity().toString());

  }

}
