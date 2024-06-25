package org.folio.rest.api;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
public class PrintEventsAPITest extends ApiTests{
  private final AssertingRecordClient printEventsClient =
    new AssertingRecordClient(
      client, StorageTestSuite.TENANT_ID, InterfaceUrls::printEventsUrl,
      "circulation-settings");

  @Test
  public void canCreatePrintEventLog() throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {
    String id = UUID.randomUUID().toString();
    JsonObject printEventsJson = getPrintEvent(id);
    JsonObject printEventsResponse = printEventsClient.create(printEventsJson).getJson();
    assertThat(printEventsResponse.getString("id"), is(id));
    assertThat(printEventsResponse.getString("requestIds"),is(printEventsJson.getString("requestIds")));
    assertThat(printEventsResponse.getString("requesterId"),is(printEventsJson.getString("requesterId")));
    assertThat(printEventsResponse.getString("requesterName"),is(printEventsJson.getString("requesterName")));
  }
  private JsonObject getPrintEvent(String id) {
    List<String> requestIds = List.of("request1","request2");
    return new JsonObject()
      .put("id", id)
      .put("requestIds", requestIds)
      .put("requesterId", "sample")
      .put("requesterName", "Sample Requester")
      .put("printEventDate", "2024-06-25T14:30:00Z");
  }
}

