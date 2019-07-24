package org.folio.rest.api;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;

import static org.folio.rest.api.RequestsApiTest.requestStorageUrl;
import static org.folio.rest.jaxrs.model.Request.Status.fromValue;
import static org.folio.rest.support.matchers.TextDateTimeMatcher.withinSecondsAfter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.PostgresClient;

class RequestUpdateTriggerTest {

  private static final String REQUEST_TABLE = "request";
  private static PostgresClient pgClient;

  @BeforeAll
  static void beforeAll() throws InterruptedException, ExecutionException, TimeoutException, IOException {
    StorageTestSuite.before();
    pgClient = PostgresClient.getInstance(StorageTestSuite.getVertx(), StorageTestSuite.TENANT_ID);
  }

  @AfterAll
  static void afterAll() throws InterruptedException, ExecutionException, TimeoutException {
    StorageTestSuite.after();
  }

  @BeforeEach
  void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(requestStorageUrl());
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Open - Awaiting pickup | Closed - Pickup expired",
    "Open - Awaiting pickup | Closed - Cancelled     "
  }, delimiter = '|')
  void awaitingPickupRequestClosedDateShouldBePresentAfterStatusTransition(String oldStatus, String newStatus)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonObject> future = new CompletableFuture<>();

    String id = "3a57dc83-e70d-404b-b1f1-442b88760331";

    Request request = new Request()
      .withStatus(fromValue(oldStatus));

    DateTime requestUpdatedDate = DateTime.now();

    saveRequest(id, request)
      .compose(v -> updateRequest(id, request.withStatus(fromValue(newStatus))))
      .compose(v -> getRequest(id))
      .setHandler(updatedRequest -> future.complete(updatedRequest.result()));

    JsonObject updatedRequest = future.get(5, TimeUnit.SECONDS);

    assertThat(updatedRequest.getString("awaitingPickupRequestClosedDate"),
      is(notNullValue()));

    assertThat(updatedRequest.getString("awaitingPickupRequestClosedDate"),
      is(withinSecondsAfter(Seconds.seconds(2), requestUpdatedDate)));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Open - Not yet filled  | Closed - Filled       ",
    "Open - Not yet filled  | Closed - Cancelled    ",
    "Open - Not yet filled  | Closed - Unfilled     ",
    "Open - Not yet filled  | Open - In transit     ",
    "Open - Not yet filled  | Open - Awaiting pickup",
    "Open - Awaiting pickup | Open - In transit     ",
    "Open - In transit      | Closed - Cancelled    ",
    "Open - In transit      | Open - Awaiting pickup",
    "Open - In transit      | Open - Not yet filled "
  }, delimiter = '|')
  void awaitingPickupRequestClosedDateShouldNotBePresentAfterStatusTransition(String oldStatus, String newStatus)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonObject> future = new CompletableFuture<>();

    String id = "3a57dc83-e70d-404b-b1f1-442b88760331";

    Request request = new Request()
      .withStatus(fromValue(oldStatus));

    saveRequest(id, request)
      .compose(v -> updateRequest(id, request.withStatus(fromValue(newStatus))))
      .compose(v -> getRequest(id))
      .setHandler(updatedRequest -> future.complete(updatedRequest.result()));

    JsonObject updatedRequest = future.get(5, TimeUnit.SECONDS);

    assertThat(updatedRequest.getString("awaitingPickupRequestClosedDate"),
      is(nullValue()));
  }

  private Future<Void> saveRequest(String id, Request request) {

    Future<String> future = Future.future();
    pgClient.save(REQUEST_TABLE, id, request, future.completer());

    return future.map(s -> null);
  }

  private Future<Void> updateRequest(String id, Request request) {

    Future<UpdateResult> future = Future.future();
    pgClient.update(REQUEST_TABLE, request, id, future.completer());

    return future.map(ur -> null);
  }

  private Future<JsonObject> getRequest(String id) {

    Future<JsonObject> future = Future.future();
    pgClient.getById(REQUEST_TABLE, id, future.completer());

    return future;
  }
}
