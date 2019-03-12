package org.folio.rest.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import static org.folio.rest.api.RequestsApiTest.requestStorageUrl;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_UNFILLED;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_AWAITING_PICKUP;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_NOT_YET_FILLED;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.folio.support.ExpirationTool;

public class RequestExpirationApiTest extends ApiTests {

  private static final String REQUEST_TABLE = "request";

  @Before
  public void beforeEach()
    throws MalformedURLException {

    StorageTestSuite.deleteAll(requestStorageUrl());
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs(REQUEST_TABLE);
  }

  @Test
  public void canExpireASingleOpenUnfilledRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withRequestExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response.containsKey("position"), is(false));
  }

  @Test
  public void canExpireASingleOpenAwaitingPickupRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(1)
      .withStatus(OPEN_AWAITING_PICKUP)
      .create(),
      requestStorageUrl());

    expireRequests();

    JsonObject response = getById(requestStorageUrl(String.format("/%s", id)));

    assertThat(response.getString("status"), is(CLOSED_PICKUP_EXPIRED));
    assertThat(response.containsKey("position"), is(false));
  }

  @Test
  public void canExpireAnFirstAwaitingPickupRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    /* Status "Open - Awaiting pickup" and hold shelf expiration date in the past - should be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
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
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
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
      .withHoldShelfExpiration(new DateTime(9999, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId)
      .withPosition(3)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();

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
  public void canExpireAnFirstOpenUnfilledRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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
      .withRequestExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    /* Status "Open - Awaiting pickup" and request expiration date in the past - should NOT be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_2)
      .withItemId(itemId1)
      .withPosition(2)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    /* Status "Open - not yet filled" and hold shelf expiration date in the past - should NOT be expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_3)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(3)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_4)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(4)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_5)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(5)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_6)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(6)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();

    JsonObject response1_1 = getById(requestStorageUrl(String.format("/%s", id1_1)));
    JsonObject response1_2 = getById(requestStorageUrl(String.format("/%s", id1_2)));
    JsonObject response1_3 = getById(requestStorageUrl(String.format("/%s", id1_3)));
    JsonObject response1_4 = getById(requestStorageUrl(String.format("/%s", id1_4)));
    JsonObject response1_5 = getById(requestStorageUrl(String.format("/%s", id1_5)));
    JsonObject response1_6 = getById(requestStorageUrl(String.format("/%s", id1_6)));

    assertThat(response1_1.getString("status"), is(CLOSED_UNFILLED));
    assertThat(response1_1.containsKey("position"), is(false));

    assertThat(response1_2.getString("status"), is(OPEN_NOT_YET_FILLED));
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
  public void canExpireOpenUnfilledRequestsInTheMiddleOfAQueue()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id1_1 = UUID.randomUUID();
    UUID id1_2 = UUID.randomUUID();
    UUID id1_3 = UUID.randomUUID();
    UUID id1_4 = UUID.randomUUID();
    UUID id1_5 = UUID.randomUUID();
    UUID id1_6 = UUID.randomUUID();
    UUID itemId1 = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_1)
      .withRequestExpiration(new DateTime(9999, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(1)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    /* Expired */
    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id1_2)
      .withRequestExpiration(new DateTime(2000, 7, 30, 10, 22, 54, DateTimeZone.UTC))
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
      .withRequestExpiration(new DateTime(2001, 7, 30, 10, 22, 54, DateTimeZone.UTC))
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
      .withRequestExpiration(new DateTime(2001, 7, 30, 10, 22, 54, DateTimeZone.UTC))
      .withItemId(itemId1)
      .withPosition(6)
      .withStatus(OPEN_NOT_YET_FILLED)
      .create(),
      requestStorageUrl());

    expireRequests();

    JsonObject response1_1 = getById(requestStorageUrl(String.format("/%s", id1_1)));
    JsonObject response1_2 = getById(requestStorageUrl(String.format("/%s", id1_2)));
    JsonObject response1_3 = getById(requestStorageUrl(String.format("/%s", id1_3)));
    JsonObject response1_4 = getById(requestStorageUrl(String.format("/%s", id1_4)));
    JsonObject response1_5 = getById(requestStorageUrl(String.format("/%s", id1_5)));
    JsonObject response1_6 = getById(requestStorageUrl(String.format("/%s", id1_6)));

    assertThat(response1_1.getString("status"), is(OPEN_NOT_YET_FILLED));
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
  }

  @Test
  public void canExpireOpenUnfilledWithNoExpirationDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withHoldShelfExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
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
  public void canExpireOpenAwaitingWithNoHoldShelfExpirationDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    createEntity(
      new RequestRequestBuilder()
      .hold()
      .withId(id)
      .withRequestExpiration(new DateTime(2017, 7, 30, 10, 22, 54, DateTimeZone.UTC))
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

  private void expireRequests() throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Void> expirationCompleted = new CompletableFuture<>();
    ExpirationTool.doRequestExpiration(StorageTestSuite.getVertx()).setHandler(res -> expirationCompleted.complete(null));
    expirationCompleted.get(20, TimeUnit.SECONDS);
  }
}
