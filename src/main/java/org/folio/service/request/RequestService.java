package org.folio.service.request;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.service.event.EntityChangedEventPublisherFactory.requestEventPublisher;
import static org.folio.support.ModuleConstants.MODULE_NAME;
import static org.folio.support.ModuleConstants.REQUEST_CLASS;
import static org.folio.support.ModuleConstants.REQUEST_TABLE;

import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.persist.RequestRepository;
import org.folio.rest.impl.util.OkapiResponseUtil;
import org.folio.rest.impl.util.RequestsApiUtil;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Requests;
import org.folio.rest.jaxrs.resource.RequestStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.event.EntityChangedEventPublisher;
import org.folio.support.ResponseUtil;
import org.folio.support.ServiceHelper;

public class RequestService {

  private static final Logger log = LogManager.getLogger(RequestService.class);

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final RequestRepository repository;
  private final EntityChangedEventPublisher<String, Request> eventPublisher;
  private final ServiceHelper<Request> helper;


  public RequestService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    this.postgresClient = postgresClient(vertxContext, okapiHeaders);
    this.repository = new RequestRepository(vertxContext, okapiHeaders);
    this.eventPublisher = requestEventPublisher(vertxContext, okapiHeaders);
    this.helper = new ServiceHelper<>(repository, eventPublisher);
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
    Errors errors = RequestsApiUtil.validateRequest(request);

    if (!errors.getErrors().isEmpty()) {
      return succeededFuture(RequestStorage.PostRequestStorageRequestsResponse
          .respond422WithApplicationJson(errors));
    }

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

  public Future<Response> createOrUpdate(String requestId, Request request) {
    Errors errors = RequestsApiUtil.validateRequest(request);

    if (!errors.getErrors().isEmpty()) {
      return succeededFuture(RequestStorage.PutRequestStorageRequestsByRequestIdResponse
          .respond422WithApplicationJson(errors));
    }

    return helper.upsertAndPublishEvents(requestId, request)
        .map(checkForSamePositionInQueueError(request))
        .otherwise(err -> {
          log.error("Failed to store request: id = {}, request = [{}]",
              requestId, helper.jsonStringOrEmpty(request), err);

          return ResponseUtil.internalErrorResponse(err);
        });
  }

  private Function<Response, Response> checkForSamePositionInQueueError(Request request) {
    return response -> isSamePositionInQueueErrorOnUpsert(response)
        ? RequestStorage.PutRequestStorageRequestsByRequestIdResponse.respond422WithApplicationJson(
            samePositionInQueueError(request))
        : response;
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
  private boolean isSamePositionInQueueErrorOnUpsert(Response response) {
    return response.getStatus() == HTTP_BAD_REQUEST.toInt()
        && response.hasEntity()
        && RequestsApiUtil
        .hasSamePositionConstraintViolated(response.getEntity().toString());
  }

}
