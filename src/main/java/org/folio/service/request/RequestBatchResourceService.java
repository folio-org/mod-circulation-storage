package org.folio.service.request;

import static org.folio.rest.impl.RequestsAPI.REQUEST_TABLE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.BatchResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

public class RequestBatchResourceService {
  private static final Logger LOG = LoggerFactory.getLogger(RequestBatchResourceService.class);
  private static final String REMOVE_POSITIONS_SQL =
    "UPDATE %s.%s SET jsonb = jsonb - 'position' WHERE id::text IN (%s)";

  private final BatchResourceService batchResourceService;
  private final String tenantName;

  public RequestBatchResourceService(String tenantName,
                                     BatchResourceService batchResourceService) {

    this.batchResourceService = batchResourceService;
    this.tenantName = tenantName;
  }

  /**
   * This method executes batch update for the request table.
   * The batch package must include request copies with removed positions
   * as a first part of transaction in order to pass 'itemId - position' constraint.
   * <p>
   * Example:
   * Let's say we have following requests in the batch:
   * 1. Request A.
   * 2. Request B.
   * 3. Request C.
   * <p>
   * Then actual batch package, that will be executed, is following:
   * 1. Copy of Request A with position = null.
   * 1. Copy of Request B with position = null.
   * 1. Copy of Request C with position = null.
   * 4. Request A.
   * 5. Request B.
   * 6. Request C.
   *
   * @param requests        - List of requests to execute in batch.
   * @param onFinishHandler - Callback function.
   */
  public void executeRequestBatchUpdate(
    List<Request> requests, Handler<AsyncResult<Void>> onFinishHandler) {

    LOG.debug("Removing positions for all request to go through positions constraint");
    List<Function<SQLConnection, Future<UpdateResult>>> allBatches = new ArrayList<>();

    // Remove positions for the requests before updating them
    // in order to go through item position constraint.
    Function<SQLConnection, Future<UpdateResult>> removePositionBatch =
      removePositionsForRequestsBatch(requests);

    List<Function<SQLConnection, Future<UpdateResult>>> updateRequestsBatch =
      updateRequestsBatch(requests);

    allBatches.add(removePositionBatch);
    allBatches.addAll(updateRequestsBatch);

    LOG.info("Executing batch update, total records to update [{}] (including remove positions)",
      allBatches.size()
    );

    batchResourceService.executeBatchUpdate(allBatches, onFinishHandler);
  }

  private Function<SQLConnection, Future<UpdateResult>> removePositionsForRequestsBatch(
    List<Request> requests) {

    Set<String> uniqueRequestIds = requests.stream()
      .map(Request::getId)
      .collect(Collectors.toSet());

    String requestIdsParamsPlaceholder = uniqueRequestIds.stream()
      .map(reqId -> "?")
      .collect(Collectors.joining(", "));

    String sql = String.format(REMOVE_POSITIONS_SQL,
      PostgresClient.convertToPsqlStandard(tenantName),
      REQUEST_TABLE,
      requestIdsParamsPlaceholder
    );

    return batchResourceService
      .queryWithParamsBatchFactory(sql, uniqueRequestIds);
  }

  private List<Function<SQLConnection, Future<UpdateResult>>> updateRequestsBatch(
    List<Request> requests) {

    return requests.stream()
      .map(request -> batchResourceService
        .updateSingleEntityBatchFactory(REQUEST_TABLE, request.getId(), request))
      .collect(Collectors.toList());
  }
}
