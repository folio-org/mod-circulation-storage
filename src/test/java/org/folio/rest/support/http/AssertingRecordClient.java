package org.folio.rest.support.http;

import static org.hamcrest.junit.MatcherAssert.assertThat;

import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isCreated;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isOk;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonObject;
import javafx.beans.value.WritableIntegerValue;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.MultipleRecords;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.Builder;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.util.StringUtil;

public class AssertingRecordClient {
  private final HttpClient client;
  private final String tenantId;
  private final UrlMaker urlMaker;
  private final String collectionPropertyName;

  public AssertingRecordClient(
    HttpClient client,
    String tenantId,
    UrlMaker urlMaker,
    String collectionPropertyName) {
    this.client = client;
    this.tenantId = tenantId;
    this.urlMaker = urlMaker;
    this.collectionPropertyName = collectionPropertyName;
  }

  public IndividualResource create(Builder builder)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    return create(builder.create());
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

  public JsonResponse attemptCreate(Builder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return attemptCreate(builder.create());
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

  public IndividualResource getById(String id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    return getById(UUID.fromString(id));
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
    Builder builder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return attemptCreateOrReplace(id, builder.create());
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

    TextResponse deleteResponse = attemptDeleteById(id);

    assertThat("Failed to delete record", deleteResponse, isNoContent());
  }

  public void delete(IndividualResource toDelete) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    deleteById(UUID.fromString(toDelete.getId()));
  }

  public MultipleRecords<JsonObject> getMany(String cqlQuery) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    final JsonResponse fetchedLoansResponse = attemptGetMany(cqlQuery, null, null);

    assertThat(fetchedLoansResponse, isOk());
    return MultipleRecords.fromJson(fetchedLoansResponse.getJson(), collectionPropertyName);
  }

  public JsonResponse attemptGetMany(String cqlQuery, Integer offset, Integer limit)
    throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    final CompletableFuture<JsonResponse> fetchManyCompleted = new CompletableFuture<>();
    final StringBuilder queryParams = new StringBuilder()
      .append("query=").append(StringUtil.urlEncode(cqlQuery));

    if (offset != null) {
      queryParams.append("&offset=").append(offset);
    }
    if (limit != null) {
      queryParams.append("&limit=").append(limit);
    }

    client.get(urlMaker.combine("?" + queryParams), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(fetchManyCompleted));

    return fetchManyCompleted.get(5, TimeUnit.SECONDS);
  }

  public MultipleRecords<JsonObject> getAll()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<JsonResponse> fetchManyCompleted = new CompletableFuture<>();

    this.client.get(urlMaker.combine(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(fetchManyCompleted));

    final JsonResponse fetchedLoansResponse = fetchManyCompleted
      .get(5, TimeUnit.SECONDS);

    assertThat(fetchedLoansResponse, isOk());

    return MultipleRecords.fromJson(fetchedLoansResponse.getJson(), collectionPropertyName);
  }

  public TextResponse attemptDeleteById(UUID id)
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    CompletableFuture<TextResponse> deleteCompleted = new CompletableFuture<>();

    client.delete(urlMaker.combine(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.text(deleteCompleted));

    return deleteCompleted.get(5, TimeUnit.SECONDS);
  }

  public JsonResponse attemptPutById(JsonObject updateObject)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();
    client.put(urlMaker.combine(String.format("/%s", updateObject.getString("id"))),
      updateObject, StorageTestSuite.TENANT_ID, ResponseHandler.json(updateCompleted));

    return updateCompleted.get(5, TimeUnit.SECONDS);
  }
}
