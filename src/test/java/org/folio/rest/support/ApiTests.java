package org.folio.rest.support;

import io.vertx.core.json.JsonObject;
import org.folio.rest.api.StorageTestSuite;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

public class ApiTests {
  private static boolean runningOnOwn;

  protected final WebClient client = new WebClient(StorageTestSuite.getVertx());

  @BeforeClass
  public static void before()
    throws Exception {

    if(StorageTestSuite.isNotInitialised()) {
      System.out.println("Running test on own, initialising suite manually");
      runningOnOwn = true;
      StorageTestSuite.before();
    }
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

  protected IndividualResource createEntity(JsonObject entity, URL url)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(url,
      entity, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat(String.format("Failed to create entity: %s", postResponse.getBody()),
      postResponse.getStatusCode(), Is.is(HTTP_CREATED));

    return new IndividualResource(postResponse);
  }

  protected JsonObject getById(URL url)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(url, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    return getResponse.getJson();
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
}
