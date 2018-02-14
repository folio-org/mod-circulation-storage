package org.folio.rest.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.hamcrest.junit.MatcherAssert;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Seconds;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.matchers.TextDateTimeMatcher.equivalentTo;
import static org.folio.rest.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class RequestsApiTest {
  private static HttpClient client = new HttpClient(StorageTestSuite.getVertx());
  private final String METADATA_PROPERTY = "metaData";

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
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .toHoldShelf()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withItem("Nod", "565578437802")
      .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
      .withStatus("Open - Not yet filled")
      .create();

    client.post(requestStorageUrl(),
      requestRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), is(equivalentTo(requestDate)));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));
    assertThat(representation.getString("status"), is("Open - Not yet filled"));

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
  @Parameters({
    "Open - Not yet filled",
    "Open - Awaiting pickup",
    "Closed - Filled"
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
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

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
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

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
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

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
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

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
  public void createRequestWithoutUserHeaderCreatesNoMetadata()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(requestStorageUrl(),
      new RequestRequestBuilder().create(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject createdRequest = response.getJson();

    assertThat("Request should not have metadata property",
      createdRequest.containsKey("metadata"), is(false));
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
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(notNullValue()));
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

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .toHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
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
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));

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
  public void canUpdateAnExistingRequestAtASpecificLocation()
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
      .create();

    createRequest(createRequestRequest);

    JsonResponse getAfterCreateResponse = getById(id);

    assertThat(String.format("Failed to get request: %s", getAfterCreateResponse.getBody()),
      getAfterCreateResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<TextResponse> updateCompleted = new CompletableFuture<>();

    UUID newRequesterId = UUID.randomUUID();

    JsonObject updateRequestRequest = getAfterCreateResponse.getJson()
      .copy()
      .put("requesterId", newRequesterId.toString())
      .put("requester", new JsonObject()
        .put("lastName", "Smith")
        .put("firstName", "Jessica")
        .put("barcode", "721076398251"))
      .put("requestExpirationDate", new LocalDate(2017, 7, 30)
        .toString("yyyy-MM-dd"))
      .put("holdShelfExpirationDate", new LocalDate(2017, 8, 31)
        .toString("yyyy-MM-dd"));

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
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));

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
  public void updatedRequestHasUpdatedMetadata()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    JsonObject request = new RequestRequestBuilder().withId(id).create();

    JsonResponse createResponse = createRequest(request);

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

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .toHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withItem("Nod", "565578437802")
      .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
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
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));

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

  private static URL requestStorageUrl() throws MalformedURLException {
    return requestStorageUrl("");
  }

  private static URL requestStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/request-storage/requests" + subPath);
  }

  private JsonResponse createRequest(JsonObject requestRequest)
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
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return postResponse;
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

}
