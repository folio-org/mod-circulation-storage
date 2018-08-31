package org.folio.rest.support.http;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;

import io.vertx.core.json.JsonObject;

public class AssertingRecordClient {
  private final HttpClient client;
  private final String tenantId;
  private final UrlMaker urlMaker;

  public AssertingRecordClient(
    HttpClient client,
    String tenantId,
    UrlMaker urlMaker) {
    this.client = client;
    this.tenantId = tenantId;
    this.urlMaker = urlMaker;
  }

  public IndividualResource create(JsonObject representation)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    JsonResponse response = attemptCreate(representation);

    assertThat(String.format("Failed to create record: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  public JsonResponse attemptCreate(JsonObject representation)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(urlMaker.combine(""), representation, this.tenantId,
      ResponseHandler.json(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  public IndividualResource getById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    JsonResponse response = attemptGetById(id);

    assertThat(String.format("Failed to get record: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    return new IndividualResource(response);
  }

  public JsonResponse attemptGetById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    URL getInstanceUrl = urlMaker.combine(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  public JsonResponse attemptCreateOrReplace(
    String id,
    JsonObject representation)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture<>();

    client.put(urlMaker.combine(String.format("/%s", id)),
      representation, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(putCompleted));

    return putCompleted.get(5, TimeUnit.SECONDS);
  }
}
