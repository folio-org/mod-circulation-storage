package org.folio.rest.support;

import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.vertx.core.json.JsonObject.mapFrom;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.getVertx;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.removeAllEvents;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.configuration.TlrSettingsConfiguration;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.KvConfigurations;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

public class ApiTests {
  private static boolean runningOnOwn;

  protected static PostgresClient pgClient;
  protected final OkapiHttpClient client = new OkapiHttpClient(getVertx());
  protected static FakeKafkaConsumer kafkaConsumer;
  private static final String CONFIGURATIONS_ENTRIES_URL_PATTERN = "/configurations/entries.*";

  @BeforeClass
  public static void before() throws Exception {
    if(StorageTestSuite.isNotInitialised()) {
      System.out.println("Running test on own, initialising suite manually");
      runningOnOwn = true;
      StorageTestSuite.before();
    }

    pgClient = PostgresClient.getInstance(getVertx(), TENANT_ID);
    kafkaConsumer = new FakeKafkaConsumer().consume(getVertx());
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

  protected void stubTlrSettings(boolean titleLevelRequestsFeatureEnabled,
    boolean createTitleLevelRequestsByDefault, boolean tlrHoldShouldFollowCirculationRules) {
    StorageTestSuite.getWireMockServer()
      .stubFor(WireMock.get(urlPathMatching("/settings.*"))
        .willReturn(ok().withBody(
          new JsonObject()
            .put("items", JsonArray.of(new JsonObject()
              .put("id", randomId())
              .put("scope", "circulation")
              .put("key", "generalTlr")
              .put("value", new JsonObject()
                .put("titleLevelRequestsFeatureEnabled", titleLevelRequestsFeatureEnabled)
                .put("createTitleLevelRequestsByDefault", createTitleLevelRequestsByDefault)
                .put("tlrHoldShouldFollowCirculationRules", tlrHoldShouldFollowCirculationRules))))
            .put("resultInfo", new JsonObject()
              .put("totalRecords", 1)
              .put("diagnostics", JsonArray.of()))
            .encodePrettily())));
  }

  protected void stub404ForTlrSettings() {
    StorageTestSuite.getWireMockServer()
      .stubFor(WireMock.get(urlPathMatching("/settings.*"))
        .willReturn(notFound().withBody("Resource not found")));
  }

  protected void stubTlrConfiguration(boolean isTlrEnabled) {
    final var tlrSettingsConfiguration = new TlrSettingsConfiguration(
      isTlrEnabled, false, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    StorageTestSuite.getWireMockServer().stubFor(WireMock.get(urlPathMatching(
        CONFIGURATIONS_ENTRIES_URL_PATTERN))
      .willReturn(ok().withBody(mapFrom(
        new KvConfigurations()
          .withConfigs(List.of(new Config()
            .withValue(mapFrom(tlrSettingsConfiguration).encodePrettily()))))
        .encodePrettily())));
  }

  protected void stub404ForTlrConfiguration() {
    StorageTestSuite.getWireMockServer().stubFor(WireMock.get(urlPathMatching(
        CONFIGURATIONS_ENTRIES_URL_PATTERN))
      .willReturn(notFound().withBody("Resource not found")));
  }

  protected void stubWithInvalidTlrConfiguration() {
    StorageTestSuite.getWireMockServer().stubFor(WireMock.get(urlPathMatching(
        CONFIGURATIONS_ENTRIES_URL_PATTERN))
      .willReturn(ok().withBody("Invalid configurations response")));
  }

  protected void stubWithEmptyTlrConfiguration() {
    StorageTestSuite.getWireMockServer().stubFor(WireMock.get(urlPathMatching(
        CONFIGURATIONS_ENTRIES_URL_PATTERN))
      .willReturn(ok().withBody(mapFrom(
        new KvConfigurations()
          .withConfigs(Collections.<Config>emptyList()))
        .encodePrettily())));
  }

  public static <T> T waitFor(Future<T> future) {
    return waitFor(future, 10);
  }

  @SneakyThrows
  public static <T> T waitFor(Future<T> future, int timeoutSeconds) {
    return future.toCompletionStage()
      .toCompletableFuture()
      .get(timeoutSeconds, TimeUnit.SECONDS);
  }

  /**
   * Truncate one or multiple tables.
   *
   * @param tableNames  Comma separated table names
   */
  protected static void truncateTables(String tableNames) {
    waitFor(pgClient.execute("TRUNCATE " + tableNames));
  }

  protected static String randomId() {
    return UUID.randomUUID().toString();
  }
}
