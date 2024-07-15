package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.folio.rest.support.http.InterfaceUrls.printEventsUrl;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isCreated;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isUnprocessableEntity;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class PrintEventsAPITest extends ApiTests {

  @Test
  public void canCreatePrintEventLog() throws MalformedURLException, ExecutionException, InterruptedException {
    JsonObject printEventsJson = getPrintEvent();
    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    final JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isCreated());
  }

  @Test
  public void createPrintEventLogWithMissingFields() throws MalformedURLException, ExecutionException, InterruptedException {
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
  public void createPrintEventLogWithBlankFields() throws MalformedURLException, ExecutionException, InterruptedException {
    JsonObject printEventsJson = getPrintEvent();
    printEventsJson.put("requesterId", " ");
    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    final JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isUnprocessableEntity());
  }

  @Test
  public void createPrintEventLogWhenRequestListIsEmpty() throws MalformedURLException, ExecutionException, InterruptedException {
    List<String> requestIds = List.of();
    JsonObject printEventsJson = getPrintEvent();
    printEventsJson.put("requestIds", requestIds);
    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();
    client.post(printEventsUrl("/print-events-entry"), printEventsJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postCompleted));
    final JsonResponse postResponse = postCompleted.get();
    assertThat(postResponse, isUnprocessableEntity());
  }

  private JsonObject getPrintEvent() {
    List<String> requestIds = List.of("5f5751b4-e352-4121-adca-204b0c2aec43", "5f5751b4-e352-4121-adca-204b0c2aec44");
    return new JsonObject()
      .put("requestIds", requestIds)
      .put("requesterId", "5f5751b4-e352-4121-adca-204b0c2aec43")
      .put("requesterName", "requester")
      .put("printEventDate", "2024-06-25T14:30:00Z");
  }
}
