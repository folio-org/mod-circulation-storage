package org.folio.rest.api;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import static org.folio.rest.api.RequestsApiTest.requestStorageUrl;
import static org.folio.rest.jaxrs.model.Request.Status.fromValue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;

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
    "Open - Awaiting pickup | Closed - Pickup expired | true",
    "Open - Awaiting pickup | Closed - Cancelled      | true",
    "Open - Awaiting pickup | Open - Not yet filled   | false",
    "Open - Awaiting pickup | Open - In transit       | false",
    "Open - Not yet filled  | Closed - Unfilled       | false",
    "Open - Not yet filled  | Closed - Filled         | false",
  }, delimiter = '|')
  void updateRequestTriggerTest(String oldStatus,
                                String newStatus,
                                boolean awaitingPickupRequestClosedDatePresent)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Request> future = new CompletableFuture<>();

    String id = "3a57dc83-e70d-404b-b1f1-442b88760331";

    Request request = new Request()
      .withStatus(fromValue(oldStatus));

    saveRequest(id, request)
      .compose(v -> updateRequest(id, request.withStatus(fromValue(newStatus))))
      .compose(v -> getRequest(id))
      .setHandler(updatedRequest -> future.complete(updatedRequest.result()));

    Request updatedRequest = future.get(5, TimeUnit.SECONDS);

    assertThat(updatedRequest.getAwaitingPickupRequestClosedDate() != null, is(awaitingPickupRequestClosedDatePresent));
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

  private Future<Request> getRequest(String id) {

    Future<JsonObject> future = Future.future();
    pgClient.getById(REQUEST_TABLE, id, future.completer());

    return future.map(json -> json.mapTo(Request.class));
  }
}
