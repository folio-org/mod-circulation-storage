package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.util.RequestsApiUtil.hasSamePositionConstraintViolated;
import static org.folio.rest.impl.util.RequestsApiUtil.samePositionInQueueError;
import static org.folio.rest.jaxrs.resource.RequestStorageBatch.PostRequestStorageBatchRequestsResponse.respond201;
import static org.folio.rest.jaxrs.resource.RequestStorageBatch.PostRequestStorageBatchRequestsResponse.respond422WithApplicationJson;
import static org.folio.rest.jaxrs.resource.RequestStorageBatch.PostRequestStorageBatchRequestsResponse.respond500WithTextPlain;

import java.util.Map;

import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.RequestsBatch;
import org.folio.rest.jaxrs.resource.RequestStorageBatch;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.service.request.RequestBatchResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class RequestsBatchAPI implements RequestStorageBatch {
  private static final Logger log = LoggerFactory.getLogger(RequestsBatchAPI.class);

  @Validate
  @Override
  public void postRequestStorageBatchRequests(
    RequestsBatch entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    try {
      MetadataUtil.populateMetadata(entity.getRequests(), okapiHeaders);
    } catch (Throwable e) {
      String msg = "Cannot populate metadata of request list elements: " + e.getMessage();
      log.error(msg, e);
      asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(msg)));
      return;
    }

    log.info("postRequestStorageBatchRequests:: requests: {}", entity.getRequests());
    new RequestBatchResourceService(context, okapiHeaders)
      .executeRequestBatchUpdate(entity.getRequests(), updateResult -> {
        // Successfully updated
        if (updateResult.succeeded()) {
          log.debug("Batch update executed successfully");
          asyncResultHandler.handle(succeededFuture(respond201()));
          return;
        }

        // Update failed due to can not have more then one request in the same position
        if (hasSamePositionConstraintViolated(updateResult.cause())) {
          log.warn("Same position constraint violated", updateResult.cause());
          asyncResultHandler.handle(succeededFuture(
            respond422WithApplicationJson(samePositionInQueueError(null, null))
          ));
        } else {
          // Other failure occurred
          log.warn("Unhandled error occurred during update", updateResult.cause());
          asyncResultHandler.handle(succeededFuture(
            respond500WithTextPlain(updateResult.cause().getMessage())
          ));
        }
      });
  }
}
