package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.vertx.core.json.JsonObject.mapFrom;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.folio.rest.api.RequestsApiTest.requestStorageUrl;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.getVertx;
import static org.folio.rest.api.StorageTestSuite.prepareTenant;
import static org.folio.rest.support.ResponseHandler.empty;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_UNFILLED;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_AWAITING_DELIVERY;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_AWAITING_PICKUP;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_IN_TRANSIT;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_NOT_YET_FILLED;
import static org.folio.rest.support.http.InterfaceUrls.requestExpirationUrl;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getRequestEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.removeAllEvents;
import static org.folio.support.EventType.LOG_RECORD;
import static org.folio.support.LogEventPayloadField.ORIGINAL;
import static org.folio.support.LogEventPayloadField.PAYLOAD;
import static org.folio.support.LogEventPayloadField.REQUESTS;
import static org.folio.support.LogEventPayloadField.UPDATED;
import static org.folio.support.MockServer.clearPublishedEvents;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.folio.rest.configuration.TlrSettingsConfiguration;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.KvConfigurations;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.OkapiHttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.support.MockServer;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

class RequestExpirationApiTest {

  private static final String REQUEST_TABLE = "request";
  protected static OkapiHttpClient client;
  protected static FakeKafkaConsumer kafkaConsumer;

  @BeforeAll
  static void beforeAll() throws InterruptedException, ExecutionException, TimeoutException {
    if (StorageTestSuite.isNotInitialised()) {
      StorageTestSuite.before();
    }
    client = new OkapiHttpClient(getVertx());
    kafkaConsumer = new FakeKafkaConsumer().consume(getVertx());
    removeAllEvents();
  }

  @BeforeEach
  void beforeEach()
    throws MalformedURLException {

    StorageTestSuite.deleteAll(requestStorageUrl());
    stubTlrConfiguration(false);
  }

  @AfterEach
  void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs(REQUEST_TABLE);
    clearPublishedEvents();
  }

  protected static IndividualResource createEntity(JsonObject entity, URL url, String tenantId)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(url, entity, tenantId, ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create entity: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HTTP_CREATED));

    return new IndividualResource(postResponse);
  }

  protected static IndividualResource createEntity(JsonObject entity, URL url)
    throws InterruptedException, ExecutionException, TimeoutException {

    return createEntity(entity, url, TENANT_ID);
  }

  protected static JsonObject getById(URL url, String tenantId)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(url, tenantId, ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    return getResponse.getJson();
  }

  @SneakyThrows
  protected static JsonObject getById(URL url) {
    return getById(url, TENANT_ID);
  }

  protected static void stubTlrConfiguration(boolean isTlrEnabled) {
    final var tlrSettingsConfiguration = new TlrSettingsConfiguration(
      isTlrEnabled, false, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    StorageTestSuite.getWireMockServer().stubFor(WireMock.get(
        urlPathMatching("/configurations/entries.*"))
      .willReturn(WireMock.ok().withBody(mapFrom(
        new KvConfigurations()
          .withConfigs(List.of(new Config()
            .withValue(mapFrom(tlrSettingsConfiguration).encodePrettily()))))
        .encodePrettily())));
  }

  protected static void stubWithEmptyTlrConfiguration() {
    StorageTestSuite.getWireMockServer().stubFor(WireMock.get(
        urlPathMatching("/configurations/entries.*"))
      .willReturn(WireMock.ok().withBody(mapFrom(
        new KvConfigurations()
          .withConfigs(List.of()))
        .encodePrettily())));
  }

  @Test
  void canExpireASingleOpenUnfilledRequest() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);
    assertDomainEvents(List.of(Pair.of(id, CLOSED_UNFILLED)));

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response.containsKey("position"), is(false));
  }

  @Test
  void canExpireRequestWhenTlrSettingsNotSet() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {
    stubWithEmptyTlrConfiguration();
    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);
    assertDomainEvents(List.of(Pair.of(id, CLOSED_UNFILLED)));

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response.containsKey("position"), is(false));
  }

  @Test
  void canExpireASingleOpenAwaitingPickupRequest() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_AWAITING_PICKUP)
      .create(),
      requestStorageUrl());

    expireRequests();
    assertDomainEvents(List.of(Pair.of(id, CLOSED_PICKUP_EXPIRED)));

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(response.containsKey("position"), is(false));
  }

  @Test
  void canExpireSingleOpenAwaitingPickupTlrRequest() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    stubTlrConfiguration(true);
    UUID id = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
        .page()
        .withRequestLevel("Title")
        .withId(id)
        .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withInstanceId(instanceId)
        .withItemId(null)
        .withHoldingsRecordId(null)
        .withPosition(1)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);
    assertDomainEvents(List.of(Pair.of(id, CLOSED_PICKUP_EXPIRED)));

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(response.containsKey("position"), is(false));
  }

  @Test
  void closedPickupExpiredTitleLevelRequestShouldBeRemovedFromQueue()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    stubTlrConfiguration(true);
    UUID firstRequestId = UUID.randomUUID();
    UUID secondRequestId = UUID.randomUUID();
    UUID thirdRequestId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
        .page()
        .withRequestLevel("Title")
        .withId(firstRequestId)
        .withHoldShelfExpirationDate(new DateTime().plusDays(1))
        .withInstanceId(instanceId)
        .withItemId(null)
        .withHoldingsRecordId(null)
        .withPosition(1)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .page()
        .withRequestLevel("Title")
        .withId(secondRequestId)
        .withHoldShelfExpirationDate(new DateTime(2022, 1, 30, 10, 22, 54, DateTimeZone.UTC))
        .withInstanceId(instanceId)
        .withItemId(null)
        .withHoldingsRecordId(null)
        .withPosition(2)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .page()
        .withRequestLevel("Title")
        .withId(thirdRequestId)
        .withHoldShelfExpirationDate(new DateTime().plusDays(2))
        .withInstanceId(instanceId)
        .withItemId(null)
        .withHoldingsRecordId(null)
        .withPosition(3)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);
    assertDomainEvents(List.of(Pair.of(secondRequestId, CLOSED_PICKUP_EXPIRED)));

    JsonObject firstRequestById = getById(requestStorageUrl(String.format("/%s", firstRequestId)));
    assertThat(firstRequestById.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(firstRequestById.getString("position"), is("1"));

    JsonObject secondRequestById = getById(requestStorageUrl(String.format("/%s", secondRequestId)));
    assertThat(secondRequestById.getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(secondRequestById.containsKey("position"), is(false));

    JsonObject thirdRequestById = getById(requestStorageUrl(String.format("/%s", thirdRequestId)));
    assertThat(thirdRequestById.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(thirdRequestById.getString("position"), is("2"));
  }

  @Test
  void closedPickupExpiredItemLevelRequestShouldBeRemovedFromQueue()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    UUID firstRequestId = UUID.randomUUID();
    UUID secondRequestId = UUID.randomUUID();
    UUID thirdRequestId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
        .page()
        .withRequestLevel("Item")
        .withId(secondRequestId)
        .withHoldShelfExpirationDate(new DateTime(2022, 1, 30, 10, 22, 54, DateTimeZone.UTC))
        .withInstanceId(instanceId)
        .withItemId(itemId)
        .withPosition(1)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .page()
        .withRequestLevel("Item")
        .withId(firstRequestId)
        .withHoldShelfExpirationDate(new DateTime().plusDays(1))
        .withInstanceId(instanceId)
        .withItemId(itemId)
        .withPosition(2)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .page()
        .withRequestLevel("Item")
        .withId(thirdRequestId)
        .withHoldShelfExpirationDate(new DateTime().plusDays(2))
        .withInstanceId(instanceId)
        .withItemId(UUID.randomUUID())
        .withPosition(1)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);
    assertDomainEvents(List.of(Pair.of(secondRequestId, CLOSED_PICKUP_EXPIRED)));

    JsonObject firstRequestById = getById(requestStorageUrl(String.format("/%s", secondRequestId)));
    assertThat(firstRequestById.getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(firstRequestById.containsKey("position"), is(false));

    JsonObject secondRequestById = getById(requestStorageUrl(String.format("/%s", firstRequestId)));
    assertThat(secondRequestById.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(secondRequestById.getString("position"), is("1"));

    JsonObject thirdRequestById = getById(requestStorageUrl(String.format("/%s", thirdRequestId)));
    assertThat(thirdRequestById.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(thirdRequestById.getString("position"), is("1"));
  }

  @Test
  void canExpireSingleOpenAwaitingDeliveryRequest() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId)
        .withPosition(1)
        .withStatus(OPEN_AWAITING_DELIVERY)
        .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response.containsKey("position"), is(false));
  }

  @Test
  void canExpireAnFirstAwaitingPickupRequest() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    /* Status "Open - Awaiting pickup" and hold shelf expiration date in the past - should be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1)
      .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_AWAITING_PICKUP)
      .create(),
      requestStorageUrl());

    /* Status "Open - not yet filled" and request expiration date in the future - should NOT be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id2)
      .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(2)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    /* Status "Open - Awaiting pickup" and hold shelf expiration date in the future - should NOT be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id3)
      .withHoldShelfExpirationDate(new DateTime(9999, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(3)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);
    assertDomainEvents(List.of(Pair.of(id1, CLOSED_PICKUP_EXPIRED)));

    JsonObject response1 = getById(requestStorageUrl(String.format("/%s", id1)));
    JsonObject response2 = getById(requestStorageUrl(String.format("/%s", id2)));
    JsonObject response3 = getById(requestStorageUrl(String.format("/%s", id3)));

    assertThat(response1.getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(response1.containsKey("position"), is(false));

    assertThat(response2.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response2.getInteger("position"), is(1));

    assertThat(response3.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response3.getInteger("position"), is(2));
  }

  @Test
  void canExpireFirstAwaitingDeliveryRequest() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    /* Status "Open - Awaiting delivery" and expiration date in the past - should be expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id1)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId)
        .withPosition(1)
        .withStatus(OPEN_AWAITING_DELIVERY)
        .create(),
      requestStorageUrl());

    /* Status "Open - not yet filled" and request expiration date not provided - should NOT be expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id2)
        .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId)
        .withPosition(2)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    /* Status "Open - Awaiting delivery" and expiration date in the future - should NOT be expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id3)
        .withRequestExpirationDate(new DateTime(9999, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId)
        .withPosition(3)
        .withStatus(OPEN_AWAITING_DELIVERY)
        .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));

    assertPublishedEvents(events);
    assertDomainEvents(List.of(Pair.of(id1, CLOSED_UNFILLED)));

    JsonObject response1 = getById(requestStorageUrl(String.format("/%s", id1)));
    JsonObject response2 = getById(requestStorageUrl(String.format("/%s", id2)));
    JsonObject response3 = getById(requestStorageUrl(String.format("/%s", id3)));

    assertThat(response1.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response1.containsKey("position"), is(false));

    assertThat(response2.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response2.getInteger("position"), is(1));

    assertThat(response3.getString("status"), is(OPEN_AWAITING_DELIVERY));
    assertThat(response3.getInteger("position"), is(2));
  }

  @Test
  void canExpireAnFirstOpenUnfilledRequest() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id1_1 = UUID.randomUUID();
    UUID id1_2 = UUID.randomUUID();
    UUID id1_3 = UUID.randomUUID();
    UUID id1_4 = UUID.randomUUID();
    UUID id1_5 = UUID.randomUUID();
    UUID id1_6 = UUID.randomUUID();
    UUID itemId1 = UUID.randomUUID();

    /* Status "Open - not yet filled" and request expiration date in the past - should be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_1)
      .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    /* Status "Open - In transit" and request expiration date in the past - should NOT be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_2)
      .withItemId(itemId1)
      .withPosition(2)
      .withStatus(OPEN_IN_TRANSIT)
      .create(),
      requestStorageUrl());

    /* Status "Open - not yet filled" and hold shelf expiration date in the past - should NOT be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_3)
      .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(3)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_4)
      .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(4)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_5)
      .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(5)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_6)
      .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(6)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();
    assertDomainEvents(List.of(Pair.of(id1_1, CLOSED_UNFILLED)));

    JsonObject response1_1 = getById(requestStorageUrl(String.format("/%s", id1_1)));
    JsonObject response1_2 = getById(requestStorageUrl(String.format("/%s", id1_2)));
    JsonObject response1_3 = getById(requestStorageUrl(String.format("/%s", id1_3)));
    JsonObject response1_4 = getById(requestStorageUrl(String.format("/%s", id1_4)));
    JsonObject response1_5 = getById(requestStorageUrl(String.format("/%s", id1_5)));
    JsonObject response1_6 = getById(requestStorageUrl(String.format("/%s", id1_6)));

    assertThat(response1_1.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response1_1.containsKey("position"), is(false));

    assertThat(response1_2.getString("status"), is(OPEN_IN_TRANSIT));
    assertThat(response1_2.getInteger("position"), is(1));

    assertThat(response1_3.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response1_3.getInteger("position"), is(2));

    assertThat(response1_4.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response1_4.getInteger("position"), is(3));

    assertThat(response1_5.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response1_5.getInteger("position"), is(4));

    assertThat(response1_6.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response1_6.getInteger("position"), is(5));
  }

  @Test
  void canExpireOpenUnfilledRequestsInTheMiddleOfAQueue() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id1_1 = UUID.fromString("b272d1d0-cf06-45c4-9b6d-0c42a45e5084");
    UUID id1_2 = UUID.fromString("1f4ddc2a-f247-40d6-811b-cadecf0021e2");
    UUID id1_3 = UUID.fromString("d4b1458a-8b30-4122-bdf8-58b30364b407");
    UUID id1_4 = UUID.fromString("f0748267-35ce-411e-88dc-819a3d392a0d");
    UUID id1_5 = UUID.fromString("76b4c3f2-0d4b-44f9-96b6-05d48925fd6d");
    UUID id1_6 = UUID.fromString("a5c7c550-33b7-4927-bf0a-dfae4a820359");
    UUID id2_1 = UUID.fromString("3b6066fd-f338-4743-a4b9-2ed36203923d");
    UUID id2_2 = UUID.fromString("61318d02-647a-4093-b3d7-7133e5b82136");
    UUID id2_3 = UUID.fromString("36ae7cf9-0d72-4ab6-b138-27ecc4e1afd1");
    UUID id2_4 = UUID.fromString("94e23f6a-6c24-4307-b6ce-228c3d4585ca");
    UUID id2_5 = UUID.fromString("08f93e44-54f2-4b28-b1ca-c665d4c8fa95");
    UUID id2_6 = UUID.fromString("cab0d674-3fc6-49ad-8421-1f6e4536cc5f");
    UUID id3_1 = UUID.fromString("0af2c646-cd86-4cc1-8f70-45ba28db7cf3");
    UUID id3_2 = UUID.fromString("ae06cfad-c7fe-4683-92f4-f814341dde5c");
    UUID id3_3 = UUID.fromString("5953a293-1383-45f4-aec5-c8cb35bd74f3");
    UUID id3_4 = UUID.fromString("6df7af12-6e69-497d-b4e9-ce45549166df");
    UUID id3_5 = UUID.fromString("56e4db24-6410-4c86-ad4d-ffc972dae57a");
    UUID id3_6 = UUID.fromString("f1e0abe0-e060-470d-9f70-2e49cced2793");
    UUID itemId1 = UUID.fromString("d64495e6-0cde-45d1-affc-8b602ceccc95");
    UUID itemId2 = UUID.fromString("f75d5aa8-a161-4a48-b242-dc021d3715fb");
    UUID itemId3 = UUID.fromString("f9a58c45-7f3a-4e99-a556-986b8f9d1bd8");

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id1_1)
        .withItemId(itemId1)
        .withPosition(1)
        .withStatus(OPEN_IN_TRANSIT)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id1_2)
        .withRequestExpirationDate(new DateTime(2000, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId1)
        .withPosition(2)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id1_3)
        .withRequestExpirationDate(new DateTime(2001, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId1)
        .withPosition(3)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id1_4)
        .withItemId(itemId1)
        .withPosition(4)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id1_5)
        .withItemId(itemId1)
        .withPosition(5)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id1_6)
        .withRequestExpirationDate(new DateTime(2001, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId1)
        .withPosition(6)
        .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id2_1)
        .withRequestExpirationDate(new DateTime(9999, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId2)
        .withPosition(1)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id2_2)
        .withItemId(itemId2)
        .withPosition(2)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id2_3)
        .withItemId(itemId2)
        .withPosition(3)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id2_4)
        .withItemId(itemId2)
        .withPosition(4)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id2_5)
        .withItemId(itemId2)
        .withPosition(5)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id2_6)
        .withItemId(itemId2)
        .withPosition(6)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id3_1)
        .withRequestExpirationDate(new DateTime(9999, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(itemId3)
        .withPosition(1)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id3_2)
        .withItemId(itemId3)
        .withPosition(2)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id3_3)
        .withItemId(itemId3)
        .withPosition(3)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id3_4)
        .withItemId(itemId3)
        .withPosition(4)
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id3_5)
        .withItemId(itemId3)
        .withPosition(5)
        .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(id3_6)
        .withItemId(itemId3)
        .withPosition(6)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withStatus(OPEN_NOT_YET_FILLED)
        .create(),
      requestStorageUrl());

    expireRequests();

    List<JsonObject> events = Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(10));

    assertPublishedEvents(events);
    assertDomainEvents(List.of(
      Pair.of(id1_2, CLOSED_UNFILLED),
      Pair.of(id1_3, CLOSED_UNFILLED),
      Pair.of(id1_6, CLOSED_UNFILLED),
      Pair.of(id1_6, CLOSED_UNFILLED),
      Pair.of(id2_2, CLOSED_UNFILLED),
      Pair.of(id2_3, CLOSED_UNFILLED),
      Pair.of(id3_3, CLOSED_UNFILLED),
      Pair.of(id3_5, CLOSED_PICKUP_EXPIRED),
      Pair.of(id3_6, CLOSED_UNFILLED)
    ));

    JsonObject response1_1 = getById(requestStorageUrl(String.format("/%s", id1_1)));
    JsonObject response1_2 = getById(requestStorageUrl(String.format("/%s", id1_2)));
    JsonObject response1_3 = getById(requestStorageUrl(String.format("/%s", id1_3)));
    JsonObject response1_4 = getById(requestStorageUrl(String.format("/%s", id1_4)));
    JsonObject response1_5 = getById(requestStorageUrl(String.format("/%s", id1_5)));
    JsonObject response1_6 = getById(requestStorageUrl(String.format("/%s", id1_6)));

    JsonObject response2_1 = getById(requestStorageUrl(String.format("/%s", id2_1)));
    JsonObject response2_2 = getById(requestStorageUrl(String.format("/%s", id2_2)));
    JsonObject response2_3 = getById(requestStorageUrl(String.format("/%s", id2_3)));
    JsonObject response2_4 = getById(requestStorageUrl(String.format("/%s", id2_4)));
    JsonObject response2_5 = getById(requestStorageUrl(String.format("/%s", id2_5)));
    JsonObject response2_6 = getById(requestStorageUrl(String.format("/%s", id2_6)));

    JsonObject response3_1 = getById(requestStorageUrl(String.format("/%s", id3_1)));
    JsonObject response3_2 = getById(requestStorageUrl(String.format("/%s", id3_2)));
    JsonObject response3_3 = getById(requestStorageUrl(String.format("/%s", id3_3)));
    JsonObject response3_4 = getById(requestStorageUrl(String.format("/%s", id3_4)));
    JsonObject response3_5 = getById(requestStorageUrl(String.format("/%s", id3_5)));
    JsonObject response3_6 = getById(requestStorageUrl(String.format("/%s", id3_6)));

    assertThat(response1_1.getString("status"), is(OPEN_IN_TRANSIT));
    assertThat(response1_1.getInteger("position"), is(1));

    assertThat(response1_2.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response1_2.containsKey("position"), is(false));

    assertThat(response1_3.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response1_3.containsKey("position"), is(false));

    assertThat(response1_4.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response1_4.getInteger("position"), is(2));

    assertThat(response1_5.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response1_5.getInteger("position"), is(3));

    assertThat(response1_6.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response1_6.containsKey("position"), is(false));

    assertThat(response2_1.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response2_1.getInteger("position"), is(1));

    assertThat(response2_2.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response2_2.containsKey("position"), is(false));

    assertThat(response2_3.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response2_3.containsKey("position"), is(false));

    assertThat(response2_4.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response2_4.getInteger("position"), is(2));

    assertThat(response2_5.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response2_5.getInteger("position"), is(3));

    assertThat(response2_6.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response2_6.containsKey("position"), is(false));

    assertThat(response3_1.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response3_1.getInteger("position"), is(1));

    assertThat(response3_2.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response3_2.containsKey("position"), is(false));

    assertThat(response3_3.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response3_3.containsKey("position"), is(false));

    assertThat(response3_4.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response3_4.getInteger("position"), is(2));

    assertThat(response3_5.getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(response3_5.containsKey("position"), is(false));

    assertThat(response3_6.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response3_6.containsKey("position"), is(false));
  }

  @Test
  void canExpireOpenUnfilledWithNoExpirationDate() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withHoldShelfExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(OPEN_NOT_YET_FILLED));
    assertThat(response.getInteger("position"), is(1));
  }

  @Test
  void canExpireOpenAwaitingWithNoHoldShelfExpirationDate() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_AWAITING_PICKUP)
      .create(),
      requestStorageUrl());

    expireRequests();

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(response.getInteger("position"), is(1));
  }

  @Test
  @SneakyThrows
  void updatedDateIsUpdatedOnRequestExpiration() {
    UUID requestId = UUID.randomUUID();

    RequestRequestBuilder requestBuilder = new RequestRequestBuilder()
      .hold()
      .withId(requestId)
      .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(UUID.randomUUID())
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED);

    JsonObject originalRequest = createEntity(requestBuilder.create(), requestStorageUrl())
      .getJson();

    expireRequests();
    Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .until(MockServer::getPublishedEvents, hasSize(1));
    assertDomainEvents(List.of(Pair.of(requestId, CLOSED_UNFILLED)));

    JsonObject updatedRequest = getById(requestStorageUrl(String.format("/%s", requestId)));
    assertThat(updatedRequest.getString("status"), is(CLOSED_UNFILLED));
    assertThat("metadata.updatedDate should be updated upon request expiration",
      getUpdatedDate(updatedRequest).isAfter(getUpdatedDate(originalRequest)));
  }

  @Test
  @SneakyThrows
  void shouldOnlyExpireRequestsForSpecifiedTenant() {
    UUID firstRequestId = UUID.randomUUID();
    UUID firstItemId = UUID.randomUUID();
    UUID secondRequestId = UUID.randomUUID();
    UUID secondItemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
        .hold()
        .withId(firstRequestId)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withHoldShelfExpirationDate(new DateTime(2017, 7, 31, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(firstItemId)
        .withPosition(1)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl());

    String dummyTenant = "dummytenant";
    prepareTenant(dummyTenant, true);
    createEntity(new RequestRequestBuilder()
        .hold()
        .withId(secondRequestId)
        .withRequestExpirationDate(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
        .withHoldShelfExpirationDate(new DateTime(2017, 7, 31, 10, 22, 54, DateTimeZone.UTC))
        .withItemId(secondItemId)
        .withPosition(1)
        .withStatus(OPEN_AWAITING_PICKUP)
        .create(),
      requestStorageUrl(), dummyTenant);
    expireRequestsForTenant(dummyTenant);

    JsonObject requestFromDefaultTenant = getById(requestStorageUrl("/" + firstRequestId));

    assertThat(requestFromDefaultTenant.getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(requestFromDefaultTenant.getInteger("position"), is(1));

    JsonObject requestFromDummyTenant = getById(requestStorageUrl(String.format("/" + secondRequestId)), dummyTenant);
    assertThat(requestFromDummyTenant.getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(requestFromDummyTenant.getInteger("position"), nullValue());
  }

  @Test
  void shouldRecalculateQueueAfterRequestExpirationWhenNoPositionRequestExists()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

      stubTlrConfiguration(true);
      UUID firstRequestId = UUID.randomUUID();
      UUID secondRequestId = UUID.randomUUID();
      UUID thirdRequestId = UUID.randomUUID();
      UUID instanceId = UUID.randomUUID();

      createEntity(
        new RequestRequestBuilder()
          .page()
          .withRequestLevel("Title")
          .withId(firstRequestId)
          .withHoldShelfExpirationDate(new DateTime().plusDays(1))
          .withInstanceId(instanceId)
          .withItemId(null)
          .withHoldingsRecordId(null)
          .withPosition(1)
          .withStatus(OPEN_AWAITING_PICKUP)
          .create(),
        requestStorageUrl());

      createEntity(
        new RequestRequestBuilder()
          .page()
          .withRequestLevel("Title")
          .withId(secondRequestId)
          .withHoldShelfExpirationDate(new DateTime(2022, 1, 30, 10, 22, 54, DateTimeZone.UTC))
          .withInstanceId(instanceId)
          .withItemId(null)
          .withHoldingsRecordId(null)
          .withPosition(2)
          .withStatus(OPEN_AWAITING_PICKUP)
          .create(),
        requestStorageUrl());

      createEntity(
        new RequestRequestBuilder()
          .page()
          .withRequestLevel("Title")
          .withId(thirdRequestId)
          .withHoldShelfExpirationDate(new DateTime().plusDays(2))
          .withInstanceId(instanceId)
          .withItemId(null)
          .withHoldingsRecordId(null)
          .withPosition(null)
          .withStatus(OPEN_AWAITING_PICKUP)
          .create(),
        requestStorageUrl());

      expireRequests();

      JsonObject firstRequestById = getById(requestStorageUrl(String.format("/%s", firstRequestId)));
      assertThat(firstRequestById.getString("status"), is(OPEN_AWAITING_PICKUP));
      assertThat(firstRequestById.getString("position"), is("1"));

      JsonObject secondRequestById = getById(requestStorageUrl(String.format("/%s", secondRequestId)));
      assertThat(secondRequestById.getString("status"), is(CLOSED_PICKUP_EXPIRED));
      assertThat(secondRequestById.containsKey("position"), is(false));

      JsonObject thirdRequestById = getById(requestStorageUrl(String.format("/%s", thirdRequestId)));
      assertThat(thirdRequestById.getString("status"), is(OPEN_AWAITING_PICKUP));
      assertThat(thirdRequestById.getString("position"), is("2"));
  }

  @SneakyThrows
  private void expireRequestsForTenant(String tenantId) {
    final var createCompleted = new CompletableFuture<Response>();

    client.post(requestExpirationUrl(), tenantId, empty(createCompleted));

    final var postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(204));
  }

  @SneakyThrows
  private void expireRequests()  {
    expireRequestsForTenant(TENANT_ID);
  }

  private void assertPublishedEvents(List<JsonObject> events) {
    events.forEach(e -> {
      Event event = e.mapTo(Event.class);
      assertThat(event.getEventType(), is(LOG_RECORD.name()));
      JsonObject payload = new JsonObject(event.getEventPayload()).getJsonObject(PAYLOAD.value());
      Request original = payload.getJsonObject(REQUESTS.value()).getJsonObject(ORIGINAL.value()).mapTo(Request.class);
      Request updated = payload.getJsonObject(REQUESTS.value()).getJsonObject(UPDATED.value()).mapTo(Request.class);
      assertThat(original.getStatus(), not(equalTo(updated.getStatus())));
    });
  }

  private static void assertDomainEvents(List<Pair<UUID, String>> expectedRequestsIds) {
    expectedRequestsIds.forEach(assertPair -> {
      var requestEvents = Awaitility.await().atMost(10, TimeUnit.SECONDS)
          .until(() -> getRequestEvents(assertPair.getLeft().toString()), hasSize(2));
      var actualRequestEvent = new ArrayList<>(emptyIfNull(requestEvents)).get(1);

      assertThat(actualRequestEvent, notNullValue());
      JsonObject value = actualRequestEvent.value();
      assertThat(value.getString("type"), is(UPDATED.name()));

      JsonObject data =  value.getJsonObject("data");
      Request original = data.getJsonObject("old").mapTo(Request.class);
      Request updated = data.getJsonObject("new").mapTo(Request.class);
      assertThat(original.getStatus(), not(equalTo(updated.getStatus())));
      assertThat(updated.getStatus().value(), is(assertPair.getRight()));
    });
  }

  private static DateTime getUpdatedDate(JsonObject request) {
    return DateTime.parse(request.getJsonObject("metadata").getString("updatedDate"));
  }
}
