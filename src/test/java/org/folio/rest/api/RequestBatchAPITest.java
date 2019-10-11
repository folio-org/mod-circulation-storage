package org.folio.rest.api;

import static org.folio.rest.api.RequestsApiTest.requestStorageUrl;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.storageUrl;
import static org.folio.rest.support.ResponseHandler.empty;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_AWAITING_PICKUP;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.URL;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

    // 1st create some sample requests for an item
    JsonObject firstRequest = createRequestForItemAtPosition(itemId, 1);
    JsonObject secondRequest = createRequestForItemAtPosition(itemId, 2);

    // 2nd: Swap positions for these requests
    JsonObject firstRequestWithNoPosition = firstRequest.copy();
    JsonObject secondRequestWithNoPosition = secondRequest.copy();

    // 2.1 - release previous positions
    secondRequestWithNoPosition.remove("position");
    firstRequestWithNoPosition.remove("position");

    // 2.2 - set new positions
    firstRequest.put("position", 2);
    secondRequest.put("position", 1);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    JsonObject allRequests = buildBatchUpdateRequest(firstRequestWithNoPosition,
      secondRequestWithNoPosition, firstRequest, secondRequest);

    client.put(batchRequestStorageUrl(), allRequests, TENANT_ID, empty(putCompleted));

    Response response = putCompleted.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(204));

    // 3rd: Retrieve requests from DB and assert changes.
    JsonObject requestsFromDb = getAllRequestsForItem(itemId);

    JsonObject firstRequestFromDb = requestsFromDb.getJsonArray("requests").getJsonObject(0);
    JsonObject secondRequestFromDb = requestsFromDb.getJsonArray("requests").getJsonObject(1);

    assertThat(requestsFromDb.getInteger("totalRecords"), is(2));
    assertThat(firstRequestFromDb, is(firstRequest));
    assertThat(secondRequestFromDb, is(secondRequest));
  }

  @Test
  public void canRollbackTransactionOnSamePositionConstraintViolation() throws Exception {
    UUID itemId = UUID.randomUUID();

    // 1st create some sample requests for an item
    JsonObject firstRequest = createRequestForItemAtPosition(itemId, 1);
    JsonObject secondRequest = createRequestForItemAtPosition(itemId, 2);

    // 2nd: Specify same positions for the requests, so constraint violation happens
    firstRequest.put("position", 1);
    secondRequest.put("position", 1);

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture<>();
    client.put(batchRequestStorageUrl(),
      buildBatchUpdateRequest(firstRequest, secondRequest),
      TENANT_ID,
      json(putCompleted)
    );

    JsonResponse response = putCompleted.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertThat(errors.getJsonObject(0).getString("message"),
      is("Cannot have more than one request with the same position in the queue"));

    // 3rd: Retrieve requests from DB and assert that changes have not been applied.
    JsonObject requestsFromDb = getAllRequestsForItem(itemId);

    JsonObject firstRequestFromDb = requestsFromDb.getJsonArray("requests").getJsonObject(0);
    JsonObject secondRequestFromDb = requestsFromDb.getJsonArray("requests").getJsonObject(1);

    assertThat(requestsFromDb.getInteger("totalRecords"), is(2));
    assertThat(firstRequestFromDb, is(firstRequest));
    // 2nd RQ must have it's initial position - 2nd
    assertThat(secondRequestFromDb, is(secondRequest.put("position", 2)));
  }

  @Test
  public void canRollbackTransactionServerError() throws Exception {
    UUID itemId = UUID.randomUUID();

    // 1st create some sample requests for an item
    JsonObject firstRequest = createRequestForItemAtPosition(itemId, 1);
    JsonObject secondRequest = createRequestForItemAtPosition(itemId, 2);

    // 2nd: Remove ID for the request, which causes an exception and transaction to be reverted
    JsonObject firstRequestCopy = firstRequest.copy();

    firstRequestCopy.remove("id");
    secondRequest.put("position", 10);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    client.put(batchRequestStorageUrl(),
      buildBatchUpdateRequest(firstRequestCopy, secondRequest),
      TENANT_ID,
      empty(putCompleted)
    );

    // Expect server error due to Exception
    Response response = putCompleted.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(500));

    // 3rd: Retrieve requests from DB and assert that changes have not been applied.
    JsonObject requestsFromDb = getAllRequestsForItem(itemId);

    JsonObject firstRequestFromDb = requestsFromDb.getJsonArray("requests").getJsonObject(0);
    JsonObject secondRequestFromDb = requestsFromDb.getJsonArray("requests").getJsonObject(1);

    assertThat(requestsFromDb.getInteger("totalRecords"), is(2));
    assertThat(firstRequestFromDb, is(firstRequest));
    // 2nd RQ must have it's initial position - 2nd
    assertThat(secondRequestFromDb, is(secondRequest.put("position", 2)));
  }

  private JsonObject getAllRequestsForItem(UUID itemId) throws Exception {
    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("?query=itemId==%s", itemId),
      TENANT_ID, json(getRequestsCompleted));

    return getRequestsCompleted.get(5, TimeUnit.SECONDS).getJson();
  }

  private JsonObject createRequestForItemAtPosition(UUID itemId, int position) throws Exception {
    JsonObject request = createEntity(
      new RequestRequestBuilder()
        .withItemId(itemId)
        .withPosition(position)
        .recall()
        .toHoldShelf()
        .withStatus(OPEN_AWAITING_PICKUP)
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
}
