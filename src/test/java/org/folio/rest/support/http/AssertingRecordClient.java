package org.folio.rest.support.http;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.HttpClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;

import io.vertx.core.json.JsonObject;

public class AssertingRecordClient {
  private final HttpClient client;
  private final String tenantId;

  public AssertingRecordClient(HttpClient client, String tenantId) {
    this.client = client;
    this.tenantId = tenantId;
  }

  public IndividualResource create(
    JsonObject representation,
    URL baseUrl)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    this.client.post(baseUrl, representation, this.tenantId,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create record: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }
}
