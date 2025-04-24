package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.folio.rest.api.RequestsApiTest.requestStorageUrl;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.storageUrl;
import static org.folio.rest.jaxrs.model.RequestQueueReordering.RequestLevel.TITLE;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.removeAllEvents;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRequestQueueReorderingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.folio.rest.impl.RequestsBatchAPI;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.RequestQueueReordering;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import junitparams.JUnitParamsRunner;

@RunWith(JUnitParamsRunner.class)
public class RequestBatchAPITest extends ApiTests {

  @Before
  public void beforeEach() throws Exception {
    StorageTestSuite.deleteAll(requestStorageUrl());
    removeAllEvents();
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("request");
  }

  @Test
  public void canUpdateRequestPositionsInBatch() throws Exception {
    StorageTestSuite.getWireMockServer()
      .stubFor(WireMock.get(urlPathMatching("/settings.*"))
        .willReturn(ok().withBody("{ \"items\" : " +
          "[{\"id\":\"8bfafdd4-56d7-4e08-b38f-dd0db1d7ab01\"," +
          "\"scope\":\"circulation\",\"key\":\"generalTlr\"," +
          "\"value\":{" +
          "\"titleLevelRequestsFeatureEnabled\":true," +
          "\"createTitleLevelRequestsByDefault\":false," +
          "\"tlrHoldShouldFollowCirculationRules\":false}}], " +
          "\"resultInfo\": {\"totalRecords\":1,\"diagnostics\":[]}}")));

    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestAtPosition(itemId, null, 1);
    JsonObject secondRequest = createRequestAtPosition(itemId, null, 2);

    ReorderRequest firstReorderRequest = new ReorderRequest(firstRequest, 2);
    ReorderRequest secondReorderRequest = new ReorderRequest(secondRequest, 1);

    reorderRequests(
      firstReorderRequest,
      secondReorderRequest
    );

    JsonObject requestsForItemReply = getAllRequestsForItem(itemId);
    assertThat(requestsForItemReply.getInteger("totalRecords"), is(2));
    JsonArray requestsFromDb = requestsForItemReply.getJsonArray("requests");
    assertThat(requestsFromDb.size(), is(2));
    JsonObject [] r = new JsonObject [] { requestsFromDb.getJsonObject(0), requestsFromDb.getJsonObject(1) };
    if (r[0].getInteger("position") == 2) {
      r = new JsonObject [] { r[1], r[0] };
    }
    assertThat(r[0].getInteger("position"), is(1));
    assertThat(r[1].getInteger("position"), is(2));
    assertThat(r[0].getString("id"), is(secondRequest.getString("id")));
    assertThat(r[1].getString("id"), is(firstRequest.getString("id")));
  }

  @Test
  public void canCloseRequestsInBatch() throws Exception {
    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestAtPosition(itemId, null, 1);
    JsonObject secondRequest = createRequestAtPosition(itemId, null, 2);

    firstRequest.put("status", Request.Status.CLOSED_FILLED.value());
    firstRequest.remove("position");
    secondRequest.put("status", Request.Status.CLOSED_FILLED.value());
    secondRequest.remove("position");

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    client.post(batchRequestStorageUrl(),
      buildBatchUpdateRequest(firstRequest, secondRequest),
      TENANT_ID,
      ResponseHandler.empty(postCompleted)
    );

    Response response = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(201));

    JsonObject allRequestsForItem = getAllRequestsForItem(itemId);
    assertThat(allRequestsForItem.getInteger("totalRecords"), is(2));

    JsonArray allRequests = allRequestsForItem.getJsonArray("requests");
    assertEqualsWithUpdatedDateChange(firstRequest, allRequests.getJsonObject(0));
    assertEqualsWithUpdatedDateChange(secondRequest, allRequests.getJsonObject(1));
  }

  @Test
  public void canUpdateRequestFulfillmentPreferenceInBatch() throws Exception {
    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestAtPosition(itemId, null, 1);
    JsonObject secondRequest = createRequestAtPosition(itemId, null, 2);

    firstRequest.put("fulfillmentPreference", "Delivery");
    secondRequest.put("fulfillmentPreference", "Delivery");

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    client.post(batchRequestStorageUrl(),
      buildBatchUpdateRequest(firstRequest, secondRequest),
      TENANT_ID,
      ResponseHandler.empty(postCompleted)
    );

    Response response = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(201));

    JsonObject allRequestsForItem = getAllRequestsForItem(itemId);
    assertThat(allRequestsForItem.getInteger("totalRecords"), is(2));

    JsonArray allRequests = allRequestsForItem.getJsonArray("requests");
    assertEqualsWithUpdatedDateChange(firstRequest, allRequests.getJsonObject(0));
    assertEqualsWithUpdatedDateChange(secondRequest, allRequests.getJsonObject(1));
  }

  @Test
  public void willAbortBatchUpdateOnPopulateMetadataException() throws Exception {
    CompletableFuture<TextResponse> postCompleted = new CompletableFuture<>();
    new RequestsBatchAPI().postRequestStorageBatchRequests(
        null, null,
        result -> postCompleted.complete(new TextResponse(
            result.result().getStatus(), result.result().getEntity().toString())),
        null);

    TextResponse response = postCompleted.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(500));
    assertThat(response.getBody(), containsString("Cannot populate metadata"));
  }

  @Test
  public void willAbortBatchUpdateForRequestsAtTheSamePositionInAnItemsQueue() throws Exception {
    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestAtPosition(itemId, null, 1);
    JsonObject secondRequest = createRequestAtPosition(itemId, null, 2);

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

    JsonObject firstRequest = createRequestAtPosition(itemId, null, 1);
    JsonObject secondRequest = createRequestAtPosition(itemId, null, 2);

    JsonResponse reorderResponse = attemptReorderRequests(ResponseHandler::json,
      new ReorderRequest(firstRequest, 2)
    );

    assertPositionConstraintViolationError(reorderResponse);

    assertRequestsNotUpdated(itemId, firstRequest, secondRequest);
  }

  @Test
  public void willAbortBatchUpdateOnNullPointerExceptionDueToNoIdInRequest() throws Exception {
    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestAtPosition(itemId, null, 1);
    JsonObject secondRequest = createRequestAtPosition(itemId, null, 2);

    JsonObject firstRequestCopy = firstRequest.copy();
    firstRequestCopy.remove("id");

    TextResponse reorderResponse = attemptReorderRequests(ResponseHandler::text,
      new ReorderRequest(firstRequestCopy, 3),
      new ReorderRequest(secondRequest, 10)
    );
    assertThat(reorderResponse.getStatusCode(), is(500));

    assertRequestsNotUpdated(itemId, firstRequest, secondRequest);
  }

  @Test
  public void cannotInjectSqlThroughRequestId() throws Exception {
    UUID itemId = UUID.randomUUID();

    JsonObject firstRequest = createRequestAtPosition(itemId, null, 1);

    JsonObject firstRequestCopy = firstRequest.copy();
    firstRequestCopy.put("id", "1'; DELETE FROM request where id::text is not '1");

    JsonResponse reorderResponse = attemptReorderRequests(ResponseHandler::json,
      new ReorderRequest(firstRequestCopy, 3)
    );
    assertValidationError(reorderResponse,
      containsString("must match"));

    assertRequestsNotUpdated(itemId, firstRequest);
  }

  @Test
  public void shouldPublishKafkaEventWhenUpdateRequestPositionsInBatchForTheInstance()
    throws Exception {

    UUID instanceId = UUID.randomUUID();
    JsonObject firstRequest = createRequestAtPosition(null, instanceId, 1);
    JsonObject secondRequest = createRequestAtPosition(null, instanceId, 2);

    ReorderRequest firstReorderRequest = new ReorderRequest(firstRequest, 2);
    ReorderRequest secondReorderRequest = new ReorderRequest(secondRequest, 1);

    reorderRequests(firstReorderRequest, secondReorderRequest);

    JsonObject requestsForInstanceReply = getAllRequestsForInstance(instanceId);
    assertThat(requestsForInstanceReply.getInteger("totalRecords"), is(2));
    JsonArray requestsFromDb = requestsForInstanceReply.getJsonArray("requests");
    assertThat(requestsFromDb.size(), is(2));
    List<JsonObject> requestsSorted = requestsFromDb.stream()
      .map(JsonObject.class::cast)
      .sorted(Comparator.comparingInt(obj -> obj.getInteger("position")))
      .toList();
    String firstRequestId = firstRequest.getString("id");
    String secondRequestId = secondRequest.getString("id");
    assertThat(requestsSorted.get(0).getInteger("position"), is(1));
    assertThat(requestsSorted.get(1).getInteger("position"), is(2));
    assertThat(requestsSorted.get(0).getString("id"), is(secondRequestId));
    assertThat(requestsSorted.get(1).getString("id"), is(firstRequestId));

    assertRequestQueueReorderingEvent(instanceId.toString(),
      requestsSorted.get(1).getString("itemId"), List.of(firstRequestId, secondRequestId), TITLE);
  }

  private JsonObject getAllRequestsForItem(UUID itemId) throws Exception {
    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("?query=itemId==%s", itemId),
      TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    return getRequestsCompleted.get(5, TimeUnit.SECONDS).getJson();
  }

  private JsonObject getAllRequestsForInstance(UUID instanceId) throws Exception {
    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("?query=instanceId==%s", instanceId),
      TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    return getRequestsCompleted.get(5, TimeUnit.SECONDS).getJson();
  }

  private JsonObject createRequestAtPosition(UUID itemId, UUID instanceId, int position)
    throws Exception {

    RequestRequestBuilder requestBuilder = null;
    if (instanceId != null) {
      requestBuilder = new RequestRequestBuilder()
        .withInstanceId(instanceId)
        .withRequestLevel("Title");
    } else {
      requestBuilder = new RequestRequestBuilder()
        .withItemId(itemId)
        .withRequestLevel("Item");
    }
    JsonObject request = createEntity(
      requestBuilder
        .withPosition(position)
        .recall()
        .toHoldShelf()
        .withStatus(RequestRequestBuilder.OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl()
    ).getJson();

    if (itemId != null) {
      assertThat(request.getString("itemId"), is(itemId.toString()));
    } else {
      assertThat(request.getString("instanceId"), is(instanceId.toString()));
    }
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
    Function<CompletableFuture<T>, Handler<AsyncResult<HttpResponse<Buffer>>>> bodyHandler,
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

  private void assertRequestsNotUpdated(UUID itemId, JsonObject... requests) throws Exception {
    JsonObject requestsForItemReply = getAllRequestsForItem(itemId);

    assertThat(requestsForItemReply.getInteger("totalRecords"), is(requests.length));

    JsonObject[] sortedExpectedRequests = Arrays.stream(requests)
      .sorted(Comparator.comparingInt(rr -> rr.getInteger("position")))
      .toArray(JsonObject[]::new);
    JsonArray requestsFromDb = requestsForItemReply.getJsonArray("requests");

    assertThat(requestsFromDb.size(), is(sortedExpectedRequests.length));
    assertThat(requestsFromDb, hasItems(sortedExpectedRequests));
  }

  /**
   * Asserts that json1 and json2 are the same with the exception of metadta.updatedDate
   * String that must exists and must be different.
   */
  private void assertEqualsWithUpdatedDateChange(JsonObject json1, JsonObject json2) {
    String updatedDate1 = json1.getJsonObject("metadata").getString("updatedDate");
    String updatedDate2 = json2.getJsonObject("metadata").getString("updatedDate");
    assertThat("metadata.updatedDate should differ", updatedDate1, is(not(updatedDate2)));
    JsonObject jsonStripped1 = json1.copy();
    JsonObject jsonStripped2 = json2.copy();
    jsonStripped1.getJsonObject("metadata").remove("updatedDate");
    jsonStripped2.getJsonObject("metadata").remove("updatedDate");
    assertThat(jsonStripped1, is(jsonStripped2));
  }

  private void assertPositionConstraintViolationError(JsonResponse response) {
    assertValidationError(response,
      is("Cannot have more than one request with the same position in the queue"));
  }

  private void assertValidationError(
    JsonResponse response, Matcher<String> errorMessageMatcher) {

    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertThat(errors.getJsonObject(0).getString("message"),
      errorMessageMatcher);
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
