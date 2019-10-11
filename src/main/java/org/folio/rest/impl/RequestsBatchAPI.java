package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.RequestsAPI.REQUEST_TABLE;
import static org.folio.rest.impl.util.RequestsApiUtil.hasSamePositionConstraintViolated;
import static org.folio.rest.impl.util.RequestsApiUtil.samePositionInQueueError;
import static org.folio.rest.jaxrs.resource.RequestStorageBatch.PutRequestStorageBatchRequestsResponse.respond204;
import static org.folio.rest.jaxrs.resource.RequestStorageBatch.PutRequestStorageBatchRequestsResponse.respond422WithApplicationJson;
import static org.folio.rest.jaxrs.resource.RequestStorageBatch.PutRequestStorageBatchRequestsResponse.respond500WithTextPlain;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.RequestsBatch;
import org.folio.rest.jaxrs.resource.RequestStorageBatch;
import org.folio.rest.persist.PgUtil;
import org.folio.service.BatchResourceService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class RequestsBatchAPI implements RequestStorageBatch {

  @Override
  public void putRequestStorageBatchRequests(
    RequestsBatch entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    BatchResourceService updateService = new BatchResourceService(
      PgUtil.postgresClient(context, okapiHeaders),
      REQUEST_TABLE
    );

    updateService.executeBatchUpdate(entity.getTransactionMode(), entity.getRequests(), Request::getId,
      updateResult -> {
        // Successfully updated
        if (updateResult.succeeded()) {
          asyncResultHandler.handle(succeededFuture(respond204()));
          return;
        }

        // Update failed due to can not have more then one request in the same position
        if (hasSamePositionConstraintViolated(updateResult.cause())) {
          asyncResultHandler.handle(succeededFuture(
            respond422WithApplicationJson(samePositionInQueueError(null, null))
          ));
        } else {
          // Other failure occurred
          asyncResultHandler.handle(succeededFuture(
            respond500WithTextPlain(updateResult.cause().getMessage())
          ));
        }
      });
  }
}
