package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static org.folio.rest.support.http.InterfaceUrls.printEventsUrl;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isCreated;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isInternalServerError;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isOk;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isUnprocessableEntity;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

class PrintEventsAPITest extends ApiTests {

  @Test
  void canCreatePrintEventLog() throws MalformedURLException, ExecutionException, InterruptedException {
    JsonObject printEventsJson = getPrintEvent();
    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    final JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isCreated());
  }

  @Test
  void createPrintEventLogWithMissingFields() throws MalformedURLException, ExecutionException, InterruptedException {
    List<String> requestIds = List.of("5f5751b4-e352-4121-adca-204b0c2aec43", "5f5751b4-e352-4121-adca-204b0c2aec44");
    JsonObject printEventsJson = new JsonObject()
      .put("requestIds", requestIds)
      .put("requesterName", "Sample Requester")
      .put("printEventDate", "2024-06-25T14:30:00Z");
    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    final JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isUnprocessableEntity());
  }

  @Test
  void createPrintEventLogWithBlankFields() throws MalformedURLException, ExecutionException, InterruptedException {
    JsonObject printEventsJson = getPrintEvent();
    printEventsJson.put("requesterId", " ");
    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    final JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isUnprocessableEntity());
  }

  @Test
  void createPrintEventLogWhenRequestListIsEmpty() throws MalformedURLException, ExecutionException, InterruptedException {
    List<String> requestIds = List.of();
    JsonObject printEventsJson = getPrintEvent();
    printEventsJson.put("requestIds", requestIds);
    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    final JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isUnprocessableEntity());
  }

  @Test
  void createAndGetPrintEventDetails() throws MalformedURLException, ExecutionException, InterruptedException {
    List<UUID> requestIds = IntStream.range(0, 10)
      .mapToObj(notUsed -> UUID.randomUUID())
      .toList();

    // Creating print event entry for batch of requestIds
    JsonObject printEventsJson = getPrintEvent();
    printEventsJson.put("requestIds", requestIds);
    printEventsJson.put("requesterName", "requester1");
    CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isCreated());

    // Fetching the print event status details for the batch of requestIds
    CompletableFuture<JsonResponse> printEventStatusResponse = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-status"), createPrintRequestIds(requestIds), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(printEventStatusResponse));
    JsonResponse response = printEventStatusResponse.get();
    assertThat(response, isOk());
    var jsonObject = response.getJson();
    assertThat(jsonObject.getInteger("totalRecords"), is(10));
    var printEventsArray = jsonObject.getJsonArray("printEventsStatusResponses");
    IntStream.range(0, printEventsArray.size())
      .mapToObj(printEventsArray::getJsonObject)
      .forEach(printEvent -> {
        assertThat(printEvent.getInteger("count"), is(1));
        assertThat(printEvent.getString("requesterName"), is("requester1"));
        assertThat(printEvent.getString("requesterId"), is("5f5751b4-e352-4121-adca-204b0c2aec43"));
        assertThat(printEvent.getString("printEventDate"), is("2024-07-15T14:30:00.000+00:00"));
      });

    // creating another print event entry for first 5 requestIds in batch
    var requestId2 = UUID.randomUUID();
    printEventsJson = getPrintEvent();
    printEventsJson.put("requestIds", requestIds.subList(0, 5));
    printEventsJson.put("requesterId", requestId2);
    printEventsJson.put("requesterName", "requester2");
    printEventsJson.put("printEventDate", "2024-07-15T14:32:00Z");
    postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    postResponse = postCompleted.get();
    assertThat(postResponse, isCreated());

    // Fetching the print event status details for the first 5 request Ids in batch.
    // As the first 5 request ids are printed twice
    // count will be 2 and the latest requester id, name and printDate will be returned
    printEventStatusResponse = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-status"),
      createPrintRequestIds(requestIds.subList(0, 5)), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(printEventStatusResponse));
    response = printEventStatusResponse.get();
    assertThat(response, isOk());
    jsonObject = response.getJson();
    assertThat(jsonObject.getInteger("totalRecords"), is(5));
    printEventsArray = jsonObject.getJsonArray("printEventsStatusResponses");
    IntStream.range(0, printEventsArray.size())
      .mapToObj(printEventsArray::getJsonObject)
      .forEach(printEvent -> {
        assertThat(printEvent.getInteger("count"), is(2));
        assertThat(printEvent.getString("requesterName"), is("requester2"));
        assertThat(printEvent.getString("requesterId"), is(requestId2.toString()));
        assertThat(printEvent.getString("printEventDate"), is("2024-07-15T14:32:00.000+00:00"));
      });

    // Fetching the print event status details for the last 5 request Ids from batch
    printEventStatusResponse = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-status"),
      createPrintRequestIds(requestIds.subList(5, requestIds.size())), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(printEventStatusResponse));
    response = printEventStatusResponse.get();
    assertThat(response, isOk());
    jsonObject = response.getJson();
    assertThat(jsonObject.getInteger("totalRecords"), is(5));
    printEventsArray = jsonObject.getJsonArray("printEventsStatusResponses");
    IntStream.range(0, printEventsArray.size())
      .mapToObj(printEventsArray::getJsonObject)
      .forEach(printEvent -> {
        assertThat(printEvent.getInteger("count"), is(1));
        assertThat(printEvent.getString("requesterName"), is("requester1"));
        assertThat(printEvent.getString("requesterId"), is("5f5751b4-e352-4121-adca-204b0c2aec43"));
        assertThat(printEvent.getString("printEventDate"), is("2024-07-15T14:30:00.000+00:00"));
      });
  }

  @Test
  void getPrintEventStatusWithEmptyRequestIds() throws MalformedURLException, ExecutionException, InterruptedException {
    JsonObject printEventsStatusRequestJson = createPrintRequestIds(List.of());
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-status"), printEventsStatusRequestJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));
    JsonResponse postResponse = getCompleted.get();
    assertThat(postResponse, isUnprocessableEntity());
  }

  @Test
  void getPrintEventStatusWithInvalidRequestIds() throws MalformedURLException, ExecutionException, InterruptedException {
    CompletableFuture<JsonResponse> printEventStatusResponse = new CompletableFuture<>();
    JsonObject printEventsStatusRequestJson = createPrintRequestIds(List.of(UUID.randomUUID(), UUID.randomUUID()));
    client.post(printEventsUrl("/print-events-status"), printEventsStatusRequestJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(printEventStatusResponse));
    JsonResponse response = printEventStatusResponse.get();
    assertThat(response, isOk());
    var jsonObject = response.getJson();
    assertThat(jsonObject.getInteger("totalRecords"), is(0));
    assertThat(jsonObject.getJsonArray("printEventsStatusResponses").size(), is(0));
  }

  @Test
  void createPrintEventLogAndValidate5XX() throws MalformedURLException,
    ExecutionException, InterruptedException {
    JsonObject printEventsJson = getPrintEvent();
    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson,
      "INVALID_TENANT_ID",
      ResponseHandler.json(postCompleted));
    final JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isInternalServerError());
  }

  private JsonObject getPrintEvent() {
    List<String> requestIds = List.of("5f5751b4-e352-4121-adca-204b0c2aec43", "5f5751b4-e352-4121-adca-204b0c2aec44");
    return new JsonObject()
      .put("requestIds", requestIds)
      .put("requesterId", "5f5751b4-e352-4121-adca-204b0c2aec43")
      .put("requesterName", "requester")
      .put("printEventDate", "2024-07-15T14:30:00Z");
  }

  private JsonObject createPrintRequestIds(List<UUID> requestIds) {
    return new JsonObject()
      .put("requestIds", requestIds);
  }
}
