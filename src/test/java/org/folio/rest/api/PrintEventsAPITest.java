package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.hamcrest.core.Is;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isUnprocessableEntity;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class PrintEventsAPITest extends ApiTests {
  private final AssertingRecordClient printEventsClient =
    new AssertingRecordClient(
      client, StorageTestSuite.TENANT_ID, InterfaceUrls::printEventsUrl,
      " ");

  @Test
  public void canCreatePrintEventLog() throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {
    JsonObject printEventsJson = getPrintEvent();
    JsonResponse response = printEventsClient.attemptCreate(printEventsJson);
    assertThat(response.getStatusCode(), Is.is(HttpURLConnection.HTTP_CREATED));
  }

  @Test
  public void createPrintEventLogWithMissingFields() throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {
    List<String> requestIds = List.of("request1", "request2");
    JsonObject printEventsJson = new JsonObject()
      .put("requestIds", requestIds)
      .put("requesterName", "Sample Requester")
      .put("printEventDate", "2024-06-25T14:30:00Z");
    JsonResponse response = printEventsClient.attemptCreate(printEventsJson);
    assertThat(response, isUnprocessableEntity());
  }

  @Test
  public void createPrintEventLogWithBlankFields() throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {
    JsonObject printEventsJson = getPrintEvent();
    printEventsJson.put("requesterId", " ");
    JsonResponse response = printEventsClient.attemptCreate(printEventsJson);
    assertThat(response, isUnprocessableEntity());
  }

  @Test
  public void createPrintEventLogWhenRequestListIsEmpty() throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {
    List<String> requestIds = List.of();
    JsonObject printEventsJson = getPrintEvent();
    printEventsJson.put("requestIds", requestIds);
    JsonResponse response = printEventsClient.attemptCreate(printEventsJson);
    assertThat(response, isUnprocessableEntity());
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
