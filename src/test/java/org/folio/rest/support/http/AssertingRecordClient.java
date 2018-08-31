package org.folio.rest.support.http;

import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isCreated;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isOk;
import static org.hamcrest.junit.MatcherAssert.assertThat;

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
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.Builder;

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

    assertThat("Failed to create record", response, isCreated());

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

    assertThat("Failed to get record", response, isOk());

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

  public void createAtSpecificLocation(
    UUID id,
    JsonObject representation)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonResponse createResponse = attemptCreateOrReplace(
      id.toString(), representation);

    assertThat("Failed to create record", createResponse, isNoContent());
  }

  public void replace(
    String id,
    Builder builder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonResponse replaceResponse = attemptCreateOrReplace(
      id, builder.create());

    assertThat("Failed to update record", replaceResponse, isNoContent());
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

  public void deleteById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<TextResponse> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.loanStorageUrl(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.text(deleteCompleted));

    TextResponse deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Failed to delete record", deleteResponse, isNoContent());
  }
}
