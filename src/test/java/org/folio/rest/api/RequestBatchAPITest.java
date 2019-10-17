package org.folio.rest.api;

import static org.folio.rest.api.RequestsApiTest.requestStorageUrl;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.storageUrl;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestBatchAPITest extends ApiTests {

  @Before
  public void beforeEach() throws Exception {
    StorageTestSuite.deleteAll(requestStorageUrl());
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("request");
  }

  @Test
  public void canUpdateRequestPositionsInBatch() throws Exception {
    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestForItemAtPosition(itemId, 1);
    JsonObject secondRequest = createRequestForItemAtPosition(itemId, 2);

    ReorderRequest firstReorderRequest = new ReorderRequest(firstRequest, 2);
    ReorderRequest secondReorderRequest = new ReorderRequest(secondRequest, 1);

    reorderRequests(
      // We have to remove request positions before assigning new ones
      // in order to get through 'item-position' unique constraint
      new ReorderRequest(firstRequest, null),
      new ReorderRequest(secondRequest, null),
      // Then set the actual positions.
      firstReorderRequest,
      secondReorderRequest
    );

    assertRequestsUpdated(itemId, firstReorderRequest, secondReorderRequest);
  }

  @Test
  public void willAbortBatchUpdateForRequestsAtTheSamePositionInAnItemsQueue() throws Exception {
    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestForItemAtPosition(itemId, 1);
    JsonObject secondRequest = createRequestForItemAtPosition(itemId, 2);

    JsonResponse reorderResponse = attemptReorderRequests(ResponseHandler::json,
      new ReorderRequest(firstRequest, 1),
      new ReorderRequest(secondRequest, 1)
    );

    assertPositionConstraintViolationError(reorderResponse);

    assertRequestsNotUpdated(itemId, firstRequest, secondRequest);
  }

  @Test
  public void willAbortBatchUpdateWhenOnlyOnePositionIsModified() throws Exception {
    UUID itemId = UUID.randomUUID();

    // 1st create some sample requests for an item
    JsonObject firstRequest = createRequestForItemAtPosition(itemId, 1);
    JsonObject secondRequest = createRequestForItemAtPosition(itemId, 2);

    JsonResponse reorderResponse = attemptReorderRequests(ResponseHandler::json,
      new ReorderRequest(firstRequest, 2)
    );

    assertPositionConstraintViolationError(reorderResponse);

    assertRequestsNotUpdated(itemId, firstRequest, secondRequest);
  }

  @Test
  public void willAbortBatchUpdateOnNullPointerExceptionDueToNoIdInRequest() throws Exception {
    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestForItemAtPosition(itemId, 1);
    JsonObject secondRequest = createRequestForItemAtPosition(itemId, 2);

    JsonObject firstRequestCopy = firstRequest.copy();
    firstRequestCopy.remove("id");

    TextResponse reorderResponse = attemptReorderRequests(ResponseHandler::text,
      new ReorderRequest(firstRequestCopy, 3),
      new ReorderRequest(secondRequest, 10)
    );
    assertThat(reorderResponse.getStatusCode(), is(500));

    assertRequestsNotUpdated(itemId, firstRequest, secondRequest);
  }

  private JsonObject getAllRequestsForItem(UUID itemId) throws Exception {
    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("?query=itemId==%s", itemId),
      TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    return getRequestsCompleted.get(5, TimeUnit.SECONDS).getJson();
  }

  private JsonObject createRequestForItemAtPosition(UUID itemId, int position) throws Exception {
    JsonObject request = createEntity(
      new RequestRequestBuilder()
        .withItemId(itemId)
        .withPosition(position)
        .recall()
        .toHoldShelf()
        .withStatus(RequestRequestBuilder.OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl()
    ).getJson();

    assertThat(request.getString("itemId"), is(itemId.toString()));
    assertThat(request.getInteger("position"), is(position));

    return request;
  }

  private JsonObject buildBatchUpdateRequest(JsonObject... requestsToUpdate) {
    return new JsonObject()
      .put("requests", new JsonArray(Arrays.asList(requestsToUpdate)));
  }

  private URL batchRequestStorageUrl() throws Exception {
    return storageUrl("/request-storage-batch/requests");
  }

  private <T> T attemptReorderRequests(
    Function<CompletableFuture<T>, Handler<HttpClientResponse>> bodyHandler,
    ReorderRequest... requests) throws Exception {

    JsonObject[] requestsToReorder = Arrays.stream(requests)
      .map(reorderRequest -> {
        JsonObject requestCopy = reorderRequest.request.copy();
        return requestCopy.put("position", reorderRequest.newPosition);
      })
      .toArray(JsonObject[]::new);

    CompletableFuture<T> postCompleted = new CompletableFuture<>();
    client.post(batchRequestStorageUrl(),
      buildBatchUpdateRequest(requestsToReorder),
      TENANT_ID,
      bodyHandler.apply(postCompleted)
    );

    return postCompleted.get(5, TimeUnit.SECONDS);
  }

  private void reorderRequests(ReorderRequest... requests) throws Exception {
    Response response = attemptReorderRequests(ResponseHandler::empty, requests);

    assertThat(response.getStatusCode(), is(201));
  }

  private void assertRequestsUpdated(UUID itemId, ReorderRequest... requests) throws Exception {
    JsonObject requestsForItemReply = getAllRequestsForItem(itemId);

    assertThat(requestsForItemReply.size(), is(requests.length));
    assertThat(requestsForItemReply.getInteger("totalRecords"), is(requests.length));

    JsonObject[] sortedExpectedRequests = Arrays.stream(requests)
      .sorted(Comparator.comparingInt(rr -> rr.newPosition))
      .map(rr -> rr.request.copy().put("position", rr.newPosition))
      .toArray(JsonObject[]::new);
    JsonArray requestsFromDb = requestsForItemReply.getJsonArray("requests");

    assertThat(requestsFromDb, hasItems(sortedExpectedRequests));
  }

  private void assertRequestsNotUpdated(UUID itemId, JsonObject... requests) throws Exception {
    JsonObject requestsForItemReply = getAllRequestsForItem(itemId);

    assertThat(requestsForItemReply.size(), is(requests.length));
    assertThat(requestsForItemReply.getInteger("totalRecords"), is(requests.length));

    JsonObject[] sortedExpectedRequests = Arrays.stream(requests)
      .sorted(Comparator.comparingInt(rr -> rr.getInteger("position")))
      .toArray(JsonObject[]::new);
    JsonArray requestsFromDb = requestsForItemReply.getJsonArray("requests");

    assertThat(requestsFromDb, hasItems(sortedExpectedRequests));
  }

  private void assertPositionConstraintViolationError(JsonResponse response) {
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertThat(errors.getJsonObject(0).getString("message"),
      is("Cannot have more than one request with the same position in the queue"));
  }

  /**
   * Holder for request reorder operation. Holds request and new position for it.
   */
  private static class ReorderRequest {
    private final JsonObject request;
    private final Integer newPosition;

    ReorderRequest(JsonObject request, Integer newPosition) {
      this.request = request;
      this.newPosition = newPosition;
    }
  }
}
