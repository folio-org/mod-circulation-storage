package org.folio.service.request;

import java.util.ArrayList;
import java.util.List;

import org.folio.rest.jaxrs.model.Request;
import org.folio.service.BatchResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rits.cloning.Cloner;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class RequestBatchResourceService {
  private static final Logger LOG = LoggerFactory.getLogger(RequestBatchResourceService.class);

  private final BatchResourceService batchResourceService;
  private final Cloner cloner;


  public RequestBatchResourceService(BatchResourceService batchResourceService) {
    this.batchResourceService = batchResourceService;
    this.cloner = new Cloner();
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
    // We have to include requests with positions removed to the batch package
    // to go through itemId-position unique constraint.
    List<Request> requestsWithRemovedPositions = removePositionsForRequests(requests);

    List<Request> allRequestsToUpdate = new ArrayList<>(requestsWithRemovedPositions);
    allRequestsToUpdate.addAll(requests);

    LOG.info("Executing batch update, total records to update [{}] (including remove positions)",
      allRequestsToUpdate.size()
    );

    batchResourceService.executeBatchUpdate(allRequestsToUpdate,
      Request::getId, onFinishHandler);
  }

  private List<Request> removePositionsForRequests(List<Request> requests) {
    List<Request> allRequests = new ArrayList<>();

    for (Request request : requests) {
      Request requestCopyWithNoPosition = cloneRequest(request)
        .withPosition(null);

      allRequests.add(requestCopyWithNoPosition);
    }

    return allRequests;
  }

  /**
   * Makes a copy of a request object.
   *
   * @param requestToCopy - Request object to make a copy.
   * @return Copy of a request object.
   */
  private Request cloneRequest(Request requestToCopy) {
    return cloner.deepClone(requestToCopy);
  }
}
