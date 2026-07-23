package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.anonymizeRequestsURL;
import static org.folio.rest.support.matchers.RequestMatchers.isAnonymized;
import static org.folio.rest.support.matchers.RequestMatchers.isNotAnonymized;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.AnonymizeStorageRequestsResponse;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class AnonymizeRequestsApiTest extends ApiTests {
  private final AssertingRecordClient requestsClient = new AssertingRecordClient(
      client, TENANT_ID, InterfaceUrls::requestStorageUrl, "requests");

  private final String firstRequestId = UUID.randomUUID().toString();
  private final String secondRequestId = UUID.randomUUID().toString();

  @BeforeEach
  void beforeEach() throws MalformedURLException, InterruptedException,
      ExecutionException, TimeoutException {

    StorageTestSuite.deleteAll(InterfaceUrls.requestStorageUrl());

    JsonObject request1 = requestsClient.create(new RequestRequestBuilder()
        .hold()
        .toHoldShelf()
        .withId(UUID.fromString(firstRequestId))
        .withItemId(UUID.randomUUID())
        .withRequesterId(UUID.randomUUID())
        .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
        .create()).getJson();

    JsonObject request2 = requestsClient.create(new RequestRequestBuilder()
        .hold()
        .toHoldShelf()
        .withId(UUID.fromString(secondRequestId))
        .withItemId(UUID.randomUUID())
        .withRequesterId(UUID.randomUUID())
        .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
        .withProxyId(UUID.randomUUID())
        .withProxy("Stuart", "Rebecca", "6059539205")
        .create()).getJson();

    requestsClient.replace(firstRequestId, RequestRequestBuilder.from(request1).closed());
    requestsClient.replace(secondRequestId, RequestRequestBuilder.from(request2).closed());
  }

  @AfterEach
  void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("request");
  }

  @Test
  void canAnonymizeRequests() throws InterruptedException, ExecutionException,
      TimeoutException, MalformedURLException {

    final var response = anonymizeRequests(firstRequestId, secondRequestId);

    assertThat(response.getAnonymizedRequests(), containsInAnyOrder(firstRequestId, secondRequestId));
    assertThat(requestsClient.getById(firstRequestId).getJson(), isAnonymized());
    assertThat(requestsClient.getById(secondRequestId).getJson(), isAnonymized());
  }

  @Test
  void canAnonymizeAlreadyAnonymizedRequests() throws InterruptedException, ExecutionException,
      TimeoutException, MalformedURLException {

    var response = anonymizeRequests(firstRequestId, secondRequestId);

    assertThat(response.getAnonymizedRequests(), containsInAnyOrder(firstRequestId, secondRequestId));
    assertThat(requestsClient.getById(firstRequestId).getJson(), isAnonymized());
    assertThat(requestsClient.getById(secondRequestId).getJson(), isAnonymized());

    response = anonymizeRequests(firstRequestId, secondRequestId);

    assertThat(response.getAnonymizedRequests(), containsInAnyOrder(firstRequestId, secondRequestId));
    assertThat(requestsClient.getById(firstRequestId).getJson(), isAnonymized());
    assertThat(requestsClient.getById(secondRequestId).getJson(), isAnonymized());
  }

  @Test
  void onlyAnonymizesOpenRequests() throws InterruptedException, ExecutionException,
      TimeoutException, MalformedURLException {

    final var openRequestId = UUID.randomUUID().toString();

    requestsClient.create(new RequestRequestBuilder()
        .hold()
        .toHoldShelf()
        .withId(UUID.fromString(openRequestId))
        .withItemId(UUID.randomUUID())
        .withRequesterId(UUID.randomUUID())
        .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
        .create()).getJson();

    // This implementation is carried over from anonymizing loans
    // The id is returned as anonymized but actually won't be unless criteria is met
    final var response = anonymizeRequests(firstRequestId, secondRequestId, openRequestId);

    assertThat(response.getAnonymizedRequests(), containsInAnyOrder(firstRequestId, secondRequestId, openRequestId));
    assertThat(requestsClient.getById(openRequestId).getJson(), isNotAnonymized());
  }

  @Test
  void canNotAnonymizeEmptyList() throws MalformedURLException {
    JsonResponse response = attemptAnonymizeRequests();

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  void canAnonymizeInvalidAndValidUuids() throws MalformedURLException {
    final String firstNotValidId = "not valid";
    final String secondNotValidId = "null";

    final var response = anonymizeRequests(firstRequestId, secondRequestId,
        firstNotValidId, secondNotValidId);

    assertThat(response.getAnonymizedRequests(), containsInAnyOrder(firstRequestId, secondRequestId));
    assertThat(response.getNotAnonymizedRequests().size(), is(1));
    assertThat(response.getNotAnonymizedRequests().get(0).getReason(), is("invalidRequestIds"));
    assertThat(response.getNotAnonymizedRequests().get(0).getRequestIds(),
        containsInAnyOrder(firstNotValidId, secondNotValidId));
  }

  private AnonymizeStorageRequestsResponse anonymizeRequests(String... requestIds) throws MalformedURLException {
    final JsonResponse response = attemptAnonymizeRequests(requestIds);

    assertThat(response.getStatusCode(), is(200));

    return response.getJson().mapTo(AnonymizeStorageRequestsResponse.class);
  }

  private JsonResponse attemptAnonymizeRequests(String... requestIds) throws MalformedURLException {
    final var requestBody = new JsonObject()
        .put("requestIds", new JsonArray(List.of(requestIds)));

    final var completed = new CompletableFuture<JsonResponse>();

    client.post(anonymizeRequestsURL(), requestBody, TENANT_ID, json(completed));

    return get(completed);
  }
}
