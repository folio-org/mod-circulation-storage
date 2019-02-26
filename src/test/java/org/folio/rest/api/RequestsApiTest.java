package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.util.Arrays.asList;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_CANCELLED;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_FILLED;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_UNFILLED;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_AWAITING_PICKUP;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_IN_TRANSIT;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_NOT_YET_FILLED;
import static org.folio.rest.support.matchers.TextDateTimeMatcher.equivalentTo;
import static org.folio.rest.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.rest.support.matchers.ValidationResponseMatchers.isValidationResponseWhich;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.folio.support.ExpirationTool;
import org.hamcrest.junit.MatcherAssert;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class RequestsApiTest extends ApiTests {
  private static HttpClient client = new HttpClient(StorageTestSuite.getVertx());
  private final String METADATA_PROPERTY = "metadata";

  @Before
  public void beforeEach()
    throws MalformedURLException {

    StorageTestSuite.deleteAll(requestStorageUrl());
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("request");
  }

  @Test
  public void canCreateARequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID proxyId = UUID.randomUUID();
    UUID pickupServicePointId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = new DateTime(2017, 7, 30, 0, 0, DateTimeZone.UTC);
    DateTime holdShelfExpirationDate = new DateTime(2017, 8, 31, 0, 0, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .toHoldShelf()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withProxyId(proxyId)
      .withRequestExpiration(requestExpirationDate)
      .withHoldShelfExpiration(holdShelfExpirationDate)
      .withItem("Nod", "565578437802")
      .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
      .withProxy("Stuart", "Rebecca", "6059539205")
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPosition(1)
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new Tags().withTagList(asList("new", "important")))
      .create();

    client.post(requestStorageUrl(),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), is(equivalentTo(requestDate)));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("proxyUserId"), is(proxyId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is(equivalentTo(requestExpirationDate)));
    assertThat(representation.getString("holdShelfExpirationDate"), is(equivalentTo(holdShelfExpirationDate)));
    assertThat(representation.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(representation.getInteger("position"), is(1));
    assertThat(representation.getString("pickupServicePointId"), is(pickupServicePointId.toString()));

    assertThat(representation.containsKey("item"), is(true));
    assertThat(representation.getJsonObject("item").getString("title"), is("Nod"));
    assertThat(representation.getJsonObject("item").getString("barcode"), is("565578437802"));

    assertThat(representation.containsKey("requester"), is(true));

    final JsonObject requesterRepresentation = representation.getJsonObject("requester");

    assertThat(requesterRepresentation.getString("lastName"), is("Jones"));
    assertThat(requesterRepresentation.getString("firstName"), is("Stuart"));
    assertThat(requesterRepresentation.getString("middleName"), is("Anthony"));
    assertThat(requesterRepresentation.getString("barcode"), is("6837502674015"));

    assertThat("has information taken from proxying user",
      representation.containsKey("proxy"), is(true));

    final JsonObject proxyRepresentation = representation.getJsonObject("proxy");

    assertThat("last name is taken from proxying user",
      proxyRepresentation.getString("lastName"), is("Stuart"));

    assertThat("first name is taken from proxying user",
      proxyRepresentation.getString("firstName"), is("Rebecca"));

    assertThat("middle name is not taken from proxying user",
      proxyRepresentation.containsKey("middleName"), is(false));

    assertThat("barcode is taken from proxying user",
      proxyRepresentation.getString("barcode"), is("6059539205"));

    assertThat(representation.containsKey("tags"), is(true));

    final JsonObject tagsRepresentation = representation.getJsonObject("tags");

    assertThat(tagsRepresentation.containsKey("tagList"), is(true));
    assertThat(tagsRepresentation.getJsonArray("tagList"), contains("new", "important"));
  }

  @Test
  @Parameters({
    OPEN_NOT_YET_FILLED,
    OPEN_AWAITING_PICKUP,
    OPEN_IN_TRANSIT,
    CLOSED_FILLED,
    CLOSED_UNFILLED,
    CLOSED_PICKUP_EXPIRED
  })
  public void canCreateARequestWithValidStatus(String status)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .toHoldShelf()
      .withStatus(status)
      .create();

    client.post(requestStorageUrl(),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("status"), is(status));
  }

  @Test
  @Parameters({
    "Non-existent status",
    ""
  })
  public void cannotCreateARequestWithInvalidStatus(String status)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    JsonObject requestRequest = new RequestRequestBuilder()
      .withStatus(status)
      .create();

    client.post(requestStorageUrl(),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should not create request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(), containsString("Json content error"));
  }

  @Test
  public void canCreateARequestToBeFulfilledByDeliveryToAnAddress()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID deliveryAddressTypeId = UUID.randomUUID();

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .deliverToAddress(deliveryAddressTypeId)
      .withId(id)
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(requestStorageUrl(),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfilmentPreference"), is("Delivery"));
    assertThat(representation.getString("deliveryAddressTypeId"),
      is(deliveryAddressTypeId.toString()));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("/%s", representation.getString("id")),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject fetchedRepresentation = getResponse.getJson();

    assertThat(fetchedRepresentation.getString("id"), is(id.toString()));
    assertThat(fetchedRepresentation.getString("requestType"), is("Recall"));
    assertThat(fetchedRepresentation.getString("fulfilmentPreference"), is("Delivery"));
    assertThat(fetchedRepresentation.getString("deliveryAddressTypeId"),
      is(deliveryAddressTypeId.toString()));
  }

  @Test
  public void canCreateARequestWithOnlyRequiredProperties()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .toHoldShelf()
      .create();

    client.post(requestStorageUrl(),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), is(equivalentTo(requestDate)));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.containsKey("requestExpirationDate"), is(false));
    assertThat(representation.containsKey("holdShelfExpirationDate"), is(false));
    assertThat(representation.containsKey("item"), is(false));
    assertThat(representation.containsKey("requester"), is(false));
  }

  @Test
  public void createdRequestHasCreationMetadata()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    String creatorId = UUID.randomUUID().toString();

    DateTime requestMade = DateTime.now();

    client.post(requestStorageUrl(),
      new RequestRequestBuilder().create(), StorageTestSuite.TENANT_ID,
      creatorId, ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HTTP_CREATED));

    JsonObject createdRequest = postResponse.getJson();

    assertThat("Request should have metadata property",
      createdRequest.containsKey(METADATA_PROPERTY), is(true));

    JsonObject metadata = createdRequest.getJsonObject(METADATA_PROPERTY);

    assertThat("Request should have created user",
      metadata.getString("createdByUserId"), is(creatorId));

    assertThat("Request should have created date close to when request was made",
      metadata.getString("createdDate"),
      is(withinSecondsAfter(Seconds.seconds(2), requestMade)));

    //RAML-Module-Builder also populates updated information at creation time
    assertThat("Request should have updated user",
      metadata.getString("updatedByUserId"), is(creatorId));

    assertThat("Request should have update date close to when request was made",
      metadata.getString("updatedDate"),
      is(withinSecondsAfter(Seconds.seconds(2), requestMade)));
  }

  @Test
  public void canCreateARequestWithoutAnId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    JsonObject requestRequest = new RequestRequestBuilder()
      .withNoId()
      .create();

    client.post(requestStorageUrl(),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(notNullValue()));
  }

  @Test
  public void canCreateMultipleRequestsForSameItemWithNoPosition()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withNoPosition()
      .create());

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    final JsonObject secondRequest = new RequestRequestBuilder()
      .withItemId(itemId)
      .withNoPosition()
      .create();

    client.post(requestStorageUrl(),
      secondRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should create request: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));
  }

  @Test
  public void canCreateMultipleRequestsForDifferentItemsWithSamePosition()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstItemId = UUID.randomUUID();
    UUID secondItemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(firstItemId)
      .withPosition(1)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(firstItemId)
      .withPosition(2)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(secondItemId)
      .withPosition(1)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(secondItemId)
      .withPosition(2)
      .create());
  }

  @Test
  public void cannotCreateRequestForSameItemAndPosition()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(1)
      .create());

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    final JsonObject secondRequest = new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(1)
      .create();

    client.post(requestStorageUrl(),
      secondRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response, isValidationResponseWhich(hasMessage(
      "Cannot have more than one request with the same position in the queue")));
  }

  @Test
  public void canCreateMultipleClosedRequestsForTheSameItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    //TODO: Create this in the suite rather than in this test
    final UUID cancellationReasonId = UUID.fromString(createCancellationReason(
      "Cancelled at patron’s request", "Use when patron wants to request cancelling")
      .getId());

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withNoPosition()
      .withStatus(CLOSED_CANCELLED)
      .withCancellationReasonId(cancellationReasonId)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withNoPosition()
      .withStatus(CLOSED_CANCELLED)
      .withCancellationReasonId(cancellationReasonId)
      .create());
  }

  //This should not happen, but shouldn't really fail either (maybe need to check)
  @Test
  public void canCreateMultipleOpenRequestsForTheSameItemWithNoPosition()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_AWAITING_PICKUP)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withNoPosition()
      .withStatus(OPEN_NOT_YET_FILLED)
      .create());
  }

  @Test
  public void canCreateARequestAtASpecificLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = new DateTime(2017, 7, 30, 0, 0, DateTimeZone.UTC);
    DateTime holdShelfExpirationDate = new DateTime(2017, 8, 31, 0, 0, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .toHoldShelf()
      .withRequestExpiration(requestExpirationDate)
      .withHoldShelfExpiration(holdShelfExpirationDate)
      .withItem("Nod", "565578437802")
      .withRequester("Smith", "Jessica", "721076398251")
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.put(requestStorageUrl(String.format("/%s", id)),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), is(equivalentTo(requestDate)));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is(equivalentTo(requestExpirationDate)));
    assertThat(representation.getString("holdShelfExpirationDate"), is(equivalentTo(holdShelfExpirationDate)));

    assertThat(representation.containsKey("item"), is(true));
    assertThat(representation.getJsonObject("item").getString("title"), is("Nod"));
    assertThat(representation.getJsonObject("item").getString("barcode"), is("565578437802"));

    assertThat(representation.containsKey("requester"), is(true));
    assertThat(representation.getJsonObject("requester").getString("lastName"), is("Smith"));
    assertThat(representation.getJsonObject("requester").getString("firstName"), is("Jessica"));
    assertThat(representation.getJsonObject("requester").containsKey("middleName"), is(false));
    assertThat(representation.getJsonObject("requester").getString("barcode"), is("721076398251"));
  }

  @Test
  public void cannotCreateRequestAtSpecificLocationForSameItemAndPosition()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(1)
      .create());

    final UUID secondRequestId = UUID.randomUUID();

    final JsonObject secondRequest = new RequestRequestBuilder()
      .withId(secondRequestId)
      .withItemId(itemId)
      .withPosition(1)
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.put(requestStorageUrl(String.format("/%s", secondRequestId)),
      secondRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response, isValidationResponseWhich(hasMessage(
      "Cannot have more than one request with the same position in the queue")));
  }

  @Test
  public void canUpdateAnExistingRequestAtASpecificLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = new DateTime(2017, 7, 30, 0, 0, DateTimeZone.UTC);
    DateTime holdShelfExpirationDate = new DateTime(2017, 8, 31, 0, 0, DateTimeZone.UTC);

    JsonObject createRequestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .toHoldShelf()
      .withItem("Nod", "565578437802")
      .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
      .withPosition(1)
      .create();

    createRequest(createRequestRequest);

    JsonResponse getAfterCreateResponse = getById(id);

    assertThat(String.format("Failed to get request: %s", getAfterCreateResponse.getBody()),
      getAfterCreateResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<TextResponse> updateCompleted = new CompletableFuture<>();

    UUID newRequesterId = UUID.randomUUID();
    UUID proxyId = UUID.randomUUID();

    JsonObject updateRequestRequest = getAfterCreateResponse.getJson()
      .copy()
      .put("requesterId", newRequesterId.toString())
      .put("proxyUserId", proxyId.toString())
      .put("position", 2)
      .put("requester", new JsonObject()
        .put("lastName", "Smith")
        .put("firstName", "Jessica")
        .put("barcode", "721076398251"))
      .put("proxy", new JsonObject()
        .put("lastName", "Stuart")
        .put("firstName", "Rebecca")
        .put("barcode", "6059539205"))
      .put("requestExpirationDate", requestExpirationDate.toString(ISODateTimeFormat.dateTime()))
      .put("holdShelfExpirationDate", holdShelfExpirationDate.toString(ISODateTimeFormat.dateTime()));

    client.put(requestStorageUrl(String.format("/%s", id)),
      updateRequestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateCompleted));

    TextResponse response = updateCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getAfterUpdateResponse = getById(id);

    JsonObject representation = getAfterUpdateResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), is(equivalentTo(requestDate)));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(newRequesterId.toString()));
    assertThat(representation.getString("proxyUserId"), is(proxyId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is(equivalentTo(requestExpirationDate)));
    assertThat(representation.getString("holdShelfExpirationDate"), is(equivalentTo(holdShelfExpirationDate)));
    assertThat(representation.getInteger("position"), is(2));

    assertThat(representation.containsKey("item"), is(true));
    assertThat(representation.getJsonObject("item").getString("title"), is("Nod"));
    assertThat(representation.getJsonObject("item").getString("barcode"), is("565578437802"));

    assertThat(representation.containsKey("requester"), is(true));

    final JsonObject requesterRepresentation = representation.getJsonObject("requester");

    assertThat(requesterRepresentation.getString("lastName"), is("Smith"));
    assertThat(requesterRepresentation.getString("firstName"), is("Jessica"));
    assertThat(requesterRepresentation.containsKey("middleName"), is(false));
    assertThat(requesterRepresentation.getString("barcode"), is("721076398251"));

    assertThat("has information taken from proxying user",
      representation.containsKey("proxy"), is(true));

    final JsonObject proxyRepresentation = representation.getJsonObject("proxy");

    assertThat("last name is taken from proxying user",
      proxyRepresentation.getString("lastName"), is("Stuart"));

    assertThat("first name is taken from proxying user",
      proxyRepresentation.getString("firstName"), is("Rebecca"));

    assertThat("middle name is not taken from proxying user",
      proxyRepresentation.containsKey("middleName"), is(false));

    assertThat("barcode is taken from proxying user",
      proxyRepresentation.getString("barcode"), is("6059539205"));
  }

  @Test
  @Parameters({
    "Open - Awaiting pickup",
    "Closed - Filled"
  })
  public void canUpdateARequestWithValidStatus(String status)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject createRequestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .toHoldShelf()
      .withItem("Nod", "565578437802")
      .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
      .withStatus("Open - Not yet filled")
      .create();

    createRequest(createRequestRequest);

    JsonResponse getAfterCreateResponse = getById(id);

    assertThat(String.format("Failed to get request: %s", getAfterCreateResponse.getBody()),
      getAfterCreateResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<TextResponse> updateCompleted = new CompletableFuture<>();

    JsonObject updateRequestRequest = getAfterCreateResponse.getJson()
      .copy()
      .put("status", status);

    client.put(requestStorageUrl(String.format("/%s", id)),
      updateRequestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateCompleted));

    TextResponse response = updateCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getAfterUpdateResponse = getById(id);

    JsonObject representation = getAfterUpdateResponse.getJson();

    assertThat(representation.getString("status"), is(status));
  }

  @Test
  @Parameters({
    "Non-existent status",
    ""
  })
  public void cannotUpdateARequestWithInvalidStatus(String status)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject createRequestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .toHoldShelf()
      .withItem("Nod", "565578437802")
      .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
      .withStatus("Open - Not yet filled")
      .create();

    createRequest(createRequestRequest);

    JsonResponse getAfterCreateResponse = getById(id);

    assertThat(String.format("Failed to get request: %s", getAfterCreateResponse.getBody()),
      getAfterCreateResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<TextResponse> updateCompleted = new CompletableFuture<>();

    JsonObject updateRequestRequest = getAfterCreateResponse.getJson()
      .copy()
      .put("status", status);

    client.put(requestStorageUrl(String.format("/%s", id)),
      updateRequestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateCompleted));

    TextResponse response = updateCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should fail to update request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    JsonResponse getAfterUpdateResponse = getById(id);

    JsonObject representation = getAfterUpdateResponse.getJson();

    assertThat(representation.getString("status"), is("Open - Not yet filled"));
  }

  @Test
  public void cannotUpdateRequestForSameItemToAnExistingPosition()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(1)
      .create());

    final IndividualResource secondRequest = createRequest(
      new RequestRequestBuilder()
        .withItemId(itemId)
        .withPosition(2)
        .create());

    final JsonObject changedSecondRequest = secondRequest.getJson()
      .put("position", 1);

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    client.put(requestStorageUrl(String.format("/%s", secondRequest.getId())),
      changedSecondRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    JsonResponse response = updateCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response, isValidationResponseWhich(hasMessage(
      "Cannot have more than one request with the same position in the queue")));
  }

  @Test
  public void updatedRequestHasUpdatedMetadata()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    JsonObject request = new RequestRequestBuilder().withId(id).create();

    IndividualResource createResponse = createRequest(request);

    JsonObject createdMetadata = createResponse.getJson()
      .getJsonObject(METADATA_PROPERTY);

    CompletableFuture<TextResponse> updateCompleted = new CompletableFuture<>();

    String updaterId = UUID.randomUUID().toString();

    DateTime requestMade = DateTime.now();

    client.put(requestStorageUrl(String.format("/%s", id)),
      request, StorageTestSuite.TENANT_ID, updaterId,
      ResponseHandler.text(updateCompleted));

    TextResponse response = updateCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getAfterUpdateResponse = getById(id);

    JsonObject updatedRequest = getAfterUpdateResponse.getJson();

    assertThat("Request should have metadata property",
      updatedRequest.containsKey(METADATA_PROPERTY), is(true));

    JsonObject metadata = updatedRequest.getJsonObject(METADATA_PROPERTY);

    assertThat("Request should have same created user",
      metadata.getString("createdByUserId"),
      is(createdMetadata.getString("createdByUserId")));

    assertThat("Request should have same created date",
      metadata.getString("createdDate"),
      is(createdMetadata.getString("createdDate")));

    assertThat("Request should have updated user",
      metadata.getString("updatedByUserId"), is(updaterId));

    assertThat("Request should have updated date close to when request was made",
      metadata.getString("updatedDate"),
      is(withinSecondsAfter(Seconds.seconds(2), requestMade)));

    assertThat("Request should have updated date different to original updated date",
      metadata.getString("updatedDate"), is(not(createdMetadata.getString("updatedDate"))));
  }

  @Test
  public void updateRequestWithoutUserHeaderFails()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    JsonObject request = new RequestRequestBuilder().withId(id).create();

    createRequest(request);

    CompletableFuture<TextResponse> updateCompleted = new CompletableFuture<>();

    client.put(requestStorageUrl(String.format("/%s", id)),
      request, StorageTestSuite.TENANT_ID, null,
      ResponseHandler.text(updateCompleted));

    TextResponse response = updateCompleted.get(5, TimeUnit.SECONDS);

    assertThat("No user header causes JSON to be null when saved",
      response.getStatusCode(), is(500));
  }

  @Test
  public void canGetARequestById()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = new DateTime(2017, 7, 30, 0, 0, DateTimeZone.UTC);
    DateTime holdShelfExpirationDate = new DateTime(2017, 8, 31, 0, 0, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .toHoldShelf()
      .withRequestExpiration(requestExpirationDate)
      .withHoldShelfExpiration(holdShelfExpirationDate)
      .withItem("Nod", "565578437802")
      .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
      .withPosition(3)
      .create();

    createRequest(requestRequest);

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), is(equivalentTo(requestDate)));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is(equivalentTo(requestExpirationDate)));
    assertThat(representation.getString("holdShelfExpirationDate"), is(equivalentTo(holdShelfExpirationDate)));
    assertThat(representation.getInteger("position"), is(3));

    assertThat(representation.containsKey("item"), is(true));
    assertThat(representation.getJsonObject("item").getString("title"), is("Nod"));
    assertThat(representation.getJsonObject("item").getString("barcode"), is("565578437802"));

    assertThat(representation.containsKey("requester"), is(true));
    assertThat(representation.getJsonObject("requester").getString("lastName"), is("Jones"));
    assertThat(representation.getJsonObject("requester").getString("firstName"), is("Stuart"));
    assertThat(representation.getJsonObject("requester").getString("middleName"), is("Anthony"));
    assertThat(representation.getJsonObject("requester").getString("barcode"), is("6837502674015"));
  }

  @Test
  public void cannotGetRequestForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonResponse getResponse = getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canPageRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + "?limit=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(requestStorageUrl() + "?limit=4&offset=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    JsonResponse firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat(String.format("Failed to get first page of requests: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get second page of requests: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageRequests = firstPage.getJsonArray("requests");
    JsonArray secondPageRequests = secondPage.getJsonArray("requests");

    assertThat(firstPageRequests.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(7));

    assertThat(secondPageRequests.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(7));
  }

  @Test
  public void canSearchForRequestsByRequesterId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstRequester = UUID.randomUUID();
    UUID secondRequester = UUID.randomUUID();

    createRequest(new RequestRequestBuilder().withRequesterId(firstRequester).create());
    createRequest(new RequestRequestBuilder().withRequesterId(firstRequester).create());
    createRequest(new RequestRequestBuilder().withRequesterId(secondRequester).create());
    createRequest(new RequestRequestBuilder().withRequesterId(firstRequester).create());
    createRequest(new RequestRequestBuilder().withRequesterId(firstRequester).create());
    createRequest(new RequestRequestBuilder().withRequesterId(secondRequester).create());
    createRequest(new RequestRequestBuilder().withRequesterId(secondRequester).create());

    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("?query=requesterId=%s", secondRequester),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    JsonResponse getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    assertThat(wrappedRequests.getJsonArray("requests").size(), is(3));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(3));
  }

  @Test
  public void createFailRequestsByUserProxyId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID requestId = UUID.randomUUID();

    JsonObject j1 = new RequestRequestBuilder().withId(requestId).create();
    j1.put("proxyUserId", "12345");

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(requestStorageUrl(),
      j1, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(422));
  }

  @Test
  public void updateFailRequestsByUserProxyId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID requestId = UUID.randomUUID();

    JsonObject j1 = new RequestRequestBuilder().withId(requestId).create();
    String userProxy1 = UUID.randomUUID().toString();
    j1.put("proxyUserId", userProxy1);
    createRequest(j1);

    ///////////// try to update with a bad proxId ////////////////////
    j1.put("proxyUserId", "12345");

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.put(requestStorageUrl("/"+requestId.toString()),
      j1, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse putResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(422));
  }

  @Test
  public void canSearchRequestsByUserProxyId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstRequester = UUID.randomUUID();

    JsonObject j1 = new RequestRequestBuilder().withRequesterId(firstRequester).create();
    JsonObject j2 = new RequestRequestBuilder().withRequesterId(firstRequester).create();
    JsonObject j3 = new RequestRequestBuilder().withRequesterId(firstRequester).create();

    String userProxy1 = UUID.randomUUID().toString();
    String userProxy2 = UUID.randomUUID().toString();
    String userProxy3 = UUID.randomUUID().toString();

    j1.put("proxyUserId", userProxy1);
    j2.put("proxyUserId", userProxy2);
    j3.put("proxyUserId", userProxy3);

    createRequest(j1);
    createRequest(j2);
    createRequest(j3);

    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("?query=proxyUserId=%s", userProxy1),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    JsonResponse getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    assertThat(wrappedRequests.getJsonArray("requests").size(), is(1));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(1));

    CompletableFuture<JsonResponse> getRequestsCompleted2 = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("?query=proxyUserId<>%s", UUID.randomUUID().toString()),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getRequestsCompleted2));

    JsonResponse getRequestsResponse2 = getRequestsCompleted2.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse2.getBody()),
      getRequestsResponse2.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests2 = getRequestsResponse2.getJson();

    assertThat(wrappedRequests2.getJsonArray("requests").size(), is(3));
    assertThat(wrappedRequests2.getInteger("totalRecords"), is(3));
  }

  @Test
  public void canSearchForRequestsForAnItem()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID itemId = UUID.randomUUID();
    UUID otherItemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(1)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(2)
      .create());

    createRequest(new RequestRequestBuilder()
      .withItemId(otherItemId)
      .withPosition(1)
      .create());

    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestStorageUrl() + String.format("?query=itemId==%s", itemId),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    JsonResponse getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    assertThat(wrappedRequests.getJsonArray("requests").size(), is(2));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canSearchForActiveRequestsForAnItem()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException, UnsupportedEncodingException {

    UUID itemId = UUID.randomUUID();
    UUID otherItemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED).create());

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(2)
      .withStatus(OPEN_AWAITING_PICKUP).create());

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withNoPosition()
      .withStatus(CLOSED_FILLED).create());

    createRequest(new RequestRequestBuilder()
      .withItemId(otherItemId)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED).create());

    createRequest(new RequestRequestBuilder()
      .withItemId(otherItemId)
      .withPosition(2)
      .withStatus(OPEN_AWAITING_PICKUP).create());

    createRequest(new RequestRequestBuilder()
      .withItemId(otherItemId)
      .withNoPosition()
      .withStatus(CLOSED_FILLED).create());

    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    String query = URLEncoder.encode(String.format("itemId==%s and status==(\"%s\" or \"%s\")",
      itemId, OPEN_NOT_YET_FILLED, OPEN_AWAITING_PICKUP),
      "UTF-8");

    client.get(requestStorageUrl() + String.format("?query=%s", query),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    JsonResponse getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    assertThat(wrappedRequests.getJsonArray("requests").size(), is(2));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canSortRequestsByAscendingRequestDate()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException, UnsupportedEncodingException {

    UUID itemId = UUID.randomUUID();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withRequestDate(new DateTime(2018, 02, 14, 15, 10, 54, DateTimeZone.UTC))
      .withPosition(1)
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withRequestDate(new DateTime(2017, 11, 24, 12, 31, 27, DateTimeZone.UTC))
      .withPosition(2)
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withRequestDate(new DateTime(2018, 02, 04, 15, 10, 54, DateTimeZone.UTC))
      .withPosition(3)
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withRequestDate(new DateTime(2018, 01, 12, 12, 31, 27, DateTimeZone.UTC))
      .withPosition(4)
      .create()).getId();

    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    String query = URLEncoder.encode(
      String.format("itemId==%s sortBy requestDate/sort.ascending", itemId),
      "UTF-8");

    client.get(requestStorageUrl() + String.format("?query=%s", query),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    JsonResponse getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = JsonArrayHelper
      .toList(wrappedRequests.getJsonArray("requests"));

    assertThat(requests.size(), is(4));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(4));

    List<String> sortedRequestDates = requests.stream()
      .map(request -> request.getString("requestDate"))
      .collect(Collectors.toList());

    assertThat(sortedRequestDates, contains(
      "2017-11-24T12:31:27.000+0000",
      "2018-01-12T12:31:27.000+0000",
      "2018-02-04T15:10:54.000+0000",
      "2018-02-14T15:10:54.000+0000"
    ));
  }

  @Test
  public void canSortRequestsByAscendingPosition()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException, UnsupportedEncodingException {

    UUID itemId = UUID.randomUUID();

    //Deliberately create requests out of order to demonstrate sorting,
    // should not happen under normal circumstances
    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(2)
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(1)
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(4)
      .create()).getId();

    createRequest(new RequestRequestBuilder()
      .withItemId(itemId)
      .withPosition(3)
      .create()).getId();

    CompletableFuture<JsonResponse> getRequestsCompleted = new CompletableFuture<>();

    String query = URLEncoder.encode(
      String.format("itemId==%s sortBy position/sort.ascending", itemId),
      "UTF-8");

    client.get(requestStorageUrl() + String.format("?query=%s", query),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getRequestsCompleted));

    JsonResponse getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = JsonArrayHelper
      .toList(wrappedRequests.getJsonArray("requests"));

    assertThat(requests.size(), is(4));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(4));

    List<Integer> sortedPositions = requests.stream()
      .map(request -> request.getInteger("position"))
      .collect(Collectors.toList());

    assertThat(sortedPositions, contains(1, 2, 3, 4));
  }

  @Test
  public void canDeleteARequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    createRequest(new RequestRequestBuilder().withId(id).create());

    client.delete(requestStorageUrl(String.format("/%s", id.toString())),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse createResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to delete request: %s", createResponse.getBody()),
      createResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Found a deleted request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canExpireARequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    UUID id1_1 = UUID.fromString("191ce6cf-144e-48f1-bb7d-9b62d9ab8fe1");
    UUID id1_2 = UUID.fromString("be4a78c5-8c57-4a23-a320-7b807b1895e2");
    UUID id1_3 = UUID.fromString("152a2138-fe46-4230-8bfc-612c973e59e3");
    UUID id1_4 = UUID.fromString("b2a972bb-94f4-4867-9bd4-9cfc00c0d0e4");
    UUID id1_5 = UUID.fromString("d2397a85-15a5-4791-85d1-09763dc057e5");
    UUID id1_6 = UUID.fromString("af49796c-0307-43bf-98f2-51cc71a721e6");
    UUID id2_1 = UUID.fromString("b990f572-6c7a-4530-85ed-7a4b741b05d1");
    UUID id2_2 = UUID.fromString("6c7682df-942f-4439-bca3-dae29efbfd0b");
    UUID id2_3 = UUID.fromString("eb61d8fd-da9b-48c9-b86f-df755d22096e");

    UUID itemId1 = UUID.randomUUID();
    UUID itemId2 = UUID.randomUUID();

    /* Status "Open - not yet filled" and request expiration date in the past - should be expired */
    JsonObject requestRequest1_1 = new RequestRequestBuilder()
      .hold()
      .withId(id1_1)
      .withRequestExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create();
    CompletableFuture<JsonResponse> createCompleted1_1 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id1_1)),
      requestRequest1_1, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted1_1));

    /* Status "Open - Awaiting pickup" and request expiration date in the past - should NOT be expired */
    JsonObject requestRequest1_2 = new RequestRequestBuilder()
      .hold()
      .withId(id1_2)
      .withItemId(itemId1)
      .withPosition(2)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create();
    CompletableFuture<JsonResponse> createCompleted1_2 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id1_2)),
      requestRequest1_2, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted1_2));

    /* Status "Open - not yet filled" and hold shelf expiration date in the past - should NOT be expired */
    JsonObject requestRequest1_3 = new RequestRequestBuilder()
      .hold()
      .withId(id1_3)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(3)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create();
    CompletableFuture<JsonResponse> createCompleted1_3 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id1_3)),
      requestRequest1_3, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted1_3));

    JsonObject requestRequest1_4 = new RequestRequestBuilder()
      .hold()
      .withId(id1_4)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(4)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create();
    CompletableFuture<JsonResponse> createCompleted1_4 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id1_4)),
      requestRequest1_4, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted1_4));

    JsonObject requestRequest1_5 = new RequestRequestBuilder()
      .hold()
      .withId(id1_5)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(5)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create();
    CompletableFuture<JsonResponse> createCompleted1_5 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id1_5)),
      requestRequest1_5, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted1_5));

    JsonObject requestRequest1_6 = new RequestRequestBuilder()
      .hold()
      .withId(id1_6)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(6)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create();
    CompletableFuture<JsonResponse> createCompleted1_6 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id1_6)),
      requestRequest1_6, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted1_6));

    /* Status "Open - Awaiting pickup" and hold shelf expiration date in the past - should be expired */
    JsonObject requestRequest2_1 = new RequestRequestBuilder()
      .hold()
      .withId(id2_1)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId2)
      .withPosition(1)
      .withStatus(OPEN_AWAITING_PICKUP)
      .create();
    CompletableFuture<JsonResponse> createCompleted2_1 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id2_1)),
      requestRequest2_1, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted2_1));

    /* Status "Open - not yet filled" and request expiration date in the future - should NOT be expired */
    JsonObject requestRequest2_2 = new RequestRequestBuilder()
      .hold()
      .withId(id2_2)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId2)
      .withPosition(2)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create();
    CompletableFuture<JsonResponse> createCompleted2_2 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id2_2)),
      requestRequest2_2, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted2_2));

    /* Status "Open - Awaiting pickup" and hold shelf expiration date in the future - should NOT be expired */
    JsonObject requestRequest2_3 = new RequestRequestBuilder()
      .hold()
      .withId(id2_3)
      .withHoldShelfExpiration(new DateTime(9999, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId2)
      .withPosition(3)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create();
    CompletableFuture<JsonResponse> createCompleted2_3 = new CompletableFuture<>();
    client.put(requestStorageUrl(String.format("/%s", id2_3)),
      requestRequest2_3, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted2_3));

    JsonResponse response1_1 = createCompleted1_1.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response1_1.getBody()),
      response1_1.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse response1_2 = createCompleted1_2.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response1_2.getBody()),
      response1_2.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse response1_3 = createCompleted1_3.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response1_3.getBody()),
      response1_3.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse response1_4 = createCompleted1_4.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response1_4.getBody()),
      response1_4.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse response1_5 = createCompleted1_5.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response1_5.getBody()),
      response1_5.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse response1_6 = createCompleted1_6.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response1_6.getBody()),
      response1_6.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse response2_1 = createCompleted2_1.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response2_1.getBody()),
      response2_1.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse response2_2 = createCompleted2_2.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response2_2.getBody()),
      response2_2.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse response2_3 = createCompleted2_3.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create request: %s", response2_3.getBody()),
      response2_3.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Void> getExpirationCF = new CompletableFuture<>();
    ExpirationTool.doRequestExpirationForTenant(StorageTestSuite.getVertx(), StorageTestSuite.getVertx().getOrCreateContext(), StorageTestSuite.TENANT_ID).setHandler(res -> {
      getExpirationCF.complete(null);
    });
    getExpirationCF.get(5, TimeUnit.SECONDS);

    JsonResponse getResponse1_1 = getById(id1_1);
    assertThat(String.format("Failed to get request: %s", getResponse1_1.getBody()),
      getResponse1_1.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonResponse getResponse1_2 = getById(id1_2);
    assertThat(String.format("Failed to get request: %s", getResponse1_2.getBody()),
      getResponse1_2.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonResponse getResponse1_3 = getById(id1_3);
    assertThat(String.format("Failed to get request: %s", getResponse1_3.getBody()),
      getResponse1_3.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonResponse getResponse1_4 = getById(id1_4);
    assertThat(String.format("Failed to get request: %s", getResponse1_4.getBody()),
      getResponse1_4.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonResponse getResponse1_5 = getById(id1_5);
    assertThat(String.format("Failed to get request: %s", getResponse1_5.getBody()),
      getResponse1_5.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonResponse getResponse1_6 = getById(id1_6);
    assertThat(String.format("Failed to get request: %s", getResponse1_6.getBody()),
      getResponse1_6.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonResponse getResponse2_1 = getById(id2_1);
    assertThat(String.format("Failed to get request: %s", getResponse2_1.getBody()),
      getResponse2_1.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonResponse getResponse2_2 = getById(id2_2);
    assertThat(String.format("Failed to get request: %s", getResponse2_2.getBody()),
      getResponse2_2.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonResponse getResponse2_3 = getById(id2_3);
    assertThat(String.format("Failed to get request: %s", getResponse2_3.getBody()),
      getResponse2_3.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertThat(getResponse1_1.getJson().getString("status"), is(CLOSED_UNFILLED));
    assertThat(getResponse1_1.getJson().containsKey("position"), is(false));

    assertThat(getResponse1_2.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(getResponse1_2.getJson().getInteger("position"), is(1));

    assertThat(getResponse1_3.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(getResponse1_3.getJson().getInteger("position"), is(2));

    assertThat(getResponse1_4.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(getResponse1_4.getJson().getInteger("position"), is(3));

    assertThat(getResponse1_5.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(getResponse1_5.getJson().getInteger("position"), is(4));

    assertThat(getResponse1_6.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(getResponse1_6.getJson().getInteger("position"), is(5));

    assertThat(getResponse2_1.getJson().getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(getResponse2_1.getJson().containsKey("position"), is(false));

    assertThat(getResponse2_2.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(getResponse2_2.getJson().getInteger("position"), is(1));

    assertThat(getResponse2_3.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(getResponse2_3.getJson().getInteger("position"), is(2));
  }

  static URL requestStorageUrl() throws MalformedURLException {
    return requestStorageUrl("");
  }

  static URL requestStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/request-storage/requests" + subPath);
  }

  private IndividualResource createRequest(JsonObject requestRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(requestStorageUrl(),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HTTP_CREATED));

    return new IndividualResource(postResponse);
  }

  private JsonResponse getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    URL getInstanceUrl = requestStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  //TODO: Move this to separate class
  private static URL cancelReasonURL() throws MalformedURLException {
    return cancelReasonURL("");
  }

  private static URL cancelReasonURL(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl(
      "/cancellation-reason-storage/cancellation-reasons" + subPath);
  }

  private IndividualResource createCancellationReason(
    String name,
    String description)

    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final JsonObject body = new JsonObject();

    body.put("name", name);
    body.put("description", description);

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(cancelReasonURL(), body, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create cancellation reason: %s",
      postResponse.getBody()), postResponse.getStatusCode(), is(HTTP_CREATED));

    return new IndividualResource(postResponse);
  }
}
