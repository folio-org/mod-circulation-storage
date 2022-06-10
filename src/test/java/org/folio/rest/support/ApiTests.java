package org.folio.rest.support;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.removeAllEvents;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

public class ApiTests {
  private static boolean runningOnOwn;


  protected final OkapiHttpClient client = new OkapiHttpClient(StorageTestSuite.getVertx());
  protected static FakeKafkaConsumer kafkaConsumer;

  @BeforeClass
  public static void before() throws Exception {
    if(StorageTestSuite.isNotInitialised()) {
      System.out.println("Running test on own, initialising suite manually");
      runningOnOwn = true;
      StorageTestSuite.before();
    }

    Vertx vertx = StorageTestSuite.getVertx();
    kafkaConsumer = new FakeKafkaConsumer().consume(vertx);
    removeAllEvents();
  }

  @AfterClass
  public static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    if(runningOnOwn) {
      System.out.println("Running test on own, un-initialising suite manually");
      StorageTestSuite.after();
    }
  }

  protected IndividualResource createEntity(JsonObject entity, URL url, String tenantId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(url,
      entity, tenantId,
      ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat(String.format("Failed to create entity: %s", postResponse.getBody()),
      postResponse.getStatusCode(), Is.is(HTTP_CREATED));

    return new IndividualResource(postResponse);
  }

  protected IndividualResource createEntity(JsonObject entity, URL url)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    return createEntity(entity, url, StorageTestSuite.TENANT_ID);
  }

  protected JsonObject getById(URL url, String tenantId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(url, tenantId,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    return getResponse.getJson();
  }

  @SneakyThrows
  protected JsonObject getById(URL url) {
    return getById(url, StorageTestSuite.TENANT_ID);
  }

  protected void checkNotFound(URL url)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(url, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    int statusCode = getCompleted.get(5, TimeUnit.SECONDS).getStatusCode();

    assertThat(statusCode, is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @SneakyThrows
  protected <T> T get(CompletableFuture<T> future) {
    return future.get(5, TimeUnit.SECONDS);
  }
}
