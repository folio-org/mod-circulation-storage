package org.folio.service.request;

import static java.util.stream.IntStream.rangeClosed;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.support.ModuleConstants.REQUEST_TABLE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.SQLConnection;
import org.folio.service.BatchResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

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
    List<Function<SQLConnection, Future<RowSet<Row>>>> allDatabaseOperations =
      new ArrayList<>();

    // Remove positions for the requests before updating them
    // in order to go through item position constraint.
    Function<SQLConnection, Future<RowSet<Row>>> removePositionBatch =
      removePositionsForRequestsBatch(requests);

    List<Function<SQLConnection, Future<RowSet<Row>>>> updateRequestsBatch =
      updateRequestsBatch(requests);

    allDatabaseOperations.add(removePositionBatch);
    allDatabaseOperations.addAll(updateRequestsBatch);

    LOG.info("Executing batch update, total records to update [{}] (including remove positions)",
      allDatabaseOperations.size());

    batchResourceService.executeBatchUpdate(allDatabaseOperations, onFinishHandler);
  }

  private Function<SQLConnection, Future<RowSet<Row>>> removePositionsForRequestsBatch(
    List<Request> requests) {

    final Set<String> uniqueRequestIds = requests.stream()
      .map(Request::getId)
      .collect(Collectors.toSet());

    final String requestIdsParamsPlaceholder = rangeClosed(1, uniqueRequestIds.size())
      .mapToObj(paramNumber -> "$" + paramNumber)
      .collect(Collectors.joining(", "));

    final String sql = String.format(REMOVE_POSITIONS_SQL,
      convertToPsqlStandard(tenantName), REQUEST_TABLE, requestIdsParamsPlaceholder);

    return batchResourceService.queryWithParamsBatchFactory(sql, uniqueRequestIds);
  }

  private List<Function<SQLConnection, Future<RowSet<Row>>>> updateRequestsBatch(
    List<Request> requests) {

    return requests.stream()
      .map(request -> batchResourceService
        .updateSingleEntityBatchFactory(REQUEST_TABLE, request.getId(), request))
      .collect(Collectors.toList());
  }
}
