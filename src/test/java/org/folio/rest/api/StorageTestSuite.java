package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.OkapiHttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.support.MockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import lombok.SneakyThrows;

public class StorageTestSuite {

  private static final Logger log = LogManager.getLogger();

  public static final String TENANT_ID = "test_tenant";

  private static Vertx vertx;
  public static final int PROXY_PORT = NetworkUtils.nextFreePort();
  public static final int OKAPI_MOCK_PORT = NetworkUtils.nextFreePort();
  private static boolean initialised = false;
  private static MockServer mockServer;
  private static final WireMockServer wireMockServer = new WireMockServer(PROXY_PORT);

  private static final KafkaContainer kafkaContainer
    = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
      .withReuse(true);

  /**
   * Return a URL for the path and the parameters.
   *
   * <p>Example: storageUrl("/foo", "year", "2019", "name", "A & Co") may return an URL for
   * http://localhost:46131/foo?year=2019&name=A%20%26%20Co
   */
  public static URL storageUrl(String path, String... parameterKeyValue) throws MalformedURLException {
    if (parameterKeyValue.length == 0) {
      return new URL("http", "localhost", PROXY_PORT, path);
    }
    if (parameterKeyValue.length % 2 == 1) {
      throw new InvalidParameterException("Expected even number of key/value strings, found "
          + parameterKeyValue.length + ": " + String.join(", ", parameterKeyValue));
    }
    try {
      StringBuilder completePath = new StringBuilder(path);
      for (int i = 0; i < parameterKeyValue.length; i += 2) {
        completePath
          .append(i == 0 ? "?" : "&")
          .append(URLEncoder.encode(parameterKeyValue[i], StandardCharsets.UTF_8.name()))
          .append('=')
          .append(URLEncoder.encode(parameterKeyValue[i+1], StandardCharsets.UTF_8.name()));
      }
      return new URL("http", "localhost", PROXY_PORT, completePath.toString());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  public static Vertx getVertx() {
    return vertx;
  }

  public static WireMockServer getWireMockServer() {
    return wireMockServer;
  }

  @BeforeAll
  public static synchronized void before()
    throws IOException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    // If already initialized by another test class, skip
    if (initialised) {
      log.info("Test suite already initialized, skipping");
      return;
    }

    log.info("Initializing test suite...");

    vertx = Vertx.vertx();

    // Set PostgresTester only once
    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    // Check if Docker is available before trying to start Kafka
    String kafkaHost;
    String kafkaPort;

    try {
      if (DockerClientFactory.instance().isDockerAvailable()) {
        // Start Kafka container if not already running
        if (!kafkaContainer.isRunning()) {
          log.info("Starting Kafka container...");
          kafkaContainer.start();
        } else {
          log.info("Kafka container already running, reusing it");
        }

        kafkaHost = kafkaContainer.getHost();
        kafkaPort = String.valueOf(kafkaContainer.getFirstMappedPort());
        log.info("Kafka available at host={} port={}", kafkaHost, kafkaPort);
      } else {
        log.warn("Docker is not available, using fallback Kafka configuration");
        // Use environment variables or default values when Docker is not available
        kafkaHost = System.getenv().getOrDefault("KAFKA_HOST", "localhost");
        kafkaPort = System.getenv().getOrDefault("KAFKA_PORT", "9092");
        log.info("Using fallback Kafka configuration: host={} port={}", kafkaHost, kafkaPort);
      }
    } catch (Exception e) {
      log.warn("Could not start Kafka container, using fallback configuration: {}", e.getMessage());
      // Use environment variables or default values when Docker fails
      kafkaHost = System.getenv().getOrDefault("KAFKA_HOST", "localhost");
      kafkaPort = System.getenv().getOrDefault("KAFKA_PORT", "9092");
      log.info("Using fallback Kafka configuration: host={} port={}", kafkaHost, kafkaPort);
    }

    // Set both system properties and environment variables for Kafka
    System.setProperty("kafka-port", kafkaPort);
    System.setProperty("kafka-host", kafkaHost);
    System.setProperty("KAFKA_PORT", kafkaPort);
    System.setProperty("KAFKA_HOST", kafkaHost);

    // Also set the bootstrap servers property
    String bootstrapServers = kafkaHost + ":" + kafkaPort;
    System.setProperty("KAFKA_BOOTSTRAP_SERVERS", bootstrapServers);
    System.setProperty("kafka.bootstrap.servers", bootstrapServers);

    log.info("Kafka configuration set: KAFKA_HOST={}, KAFKA_PORT={}", kafkaHost, kafkaPort);

    // Start mock server before verticle to ensure pubsub endpoints are available
    mockServer = new MockServer(OKAPI_MOCK_PORT, vertx);
    mockServer.start();

    wireMockServer.start();

    int port = NetworkUtils.nextFreePort();

    DeploymentOptions options = new DeploymentOptions();
    JsonObject config = new JsonObject()
      .put("http.port", port)
      .put("KAFKA_HOST", kafkaHost)
      .put("KAFKA_PORT", kafkaPort)
      .put("kafka-host", kafkaHost)
      .put("kafka-port", kafkaPort)
      .put("REPLICATION_FACTOR", "1")
      .put("ENV", "test");
    options.setConfig(config);

    log.info("Verticle deployment config: {}", config.encodePrettily());

    // Configure WireMock stubs before starting verticle
    wireMockServer.stubFor(post(urlMatching("/pubsub/.*"))
      .atPriority(1)
      .willReturn(aResponse().proxiedFrom("http://localhost:" + OKAPI_MOCK_PORT)));

    wireMockServer.stubFor(any(anyUrl())
      .atPriority(10)
      .willReturn(aResponse().proxiedFrom("http://localhost:" + port)));

    startVerticle(options);

    prepareTenant(TENANT_ID, true);

    initialised = true;
    log.info("Test suite initialization complete");
  }

  @AfterAll
  public static synchronized void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    if (!initialised) {
      log.info("Test suite not initialized, skipping cleanup");
      return;
    }

    log.info("Cleaning up test suite...");

    initialised = false;

    try {
      removeTenant(TENANT_ID);
    } catch (Exception e) {
      log.warn("Failed to remove tenant: {}", e.getMessage());
    }

    // Don't stop Kafka container - it's reusable and will be cleaned up by Testcontainers
    // kafkaContainer.stop();

    if (mockServer != null) {
      mockServer.close();
    }

    if (wireMockServer != null && wireMockServer.isRunning()) {
      wireMockServer.stop();
    }

    CompletableFuture<String> undeploymentComplete = new CompletableFuture<>();

    if (vertx != null) {
      vertx.close()
        .onSuccess(res -> undeploymentComplete.complete(null))
        .onFailure(undeploymentComplete::completeExceptionally);

      undeploymentComplete.get(20, TimeUnit.SECONDS);
    }

    log.info("Test suite cleanup complete");
  }

  public static synchronized boolean isNotInitialised() {
    return !initialised;
  }

  public static void deleteAll(URL rootUrl) {
    OkapiHttpClient client = new OkapiHttpClient(getVertx());

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    try {
      client.delete(rootUrl, TENANT_ID, ResponseHandler.empty(deleteAllFinished));

      Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

      if (response.getStatusCode() != 204) {
        log.warn(String.format("Deleting all records at '%s' failed",
          rootUrl));
      }
    } catch (Exception e) {
      log.error("Unable to delete all resources: " + e.getMessage(), e);
      assert false;
    }
  }

  public static void cleanUpTable(String tableName) {
    CompletableFuture<Void> removeCompleted = new CompletableFuture<>();

    PostgresClient.getInstance(getVertx(), TENANT_ID)
      .delete(tableName, new Criterion(), updateResult -> {
        if (updateResult.succeeded()) {
          removeCompleted.complete(null);
        } else {
          removeCompleted.completeExceptionally(updateResult.cause());
        }
      });

    try {
      removeCompleted.get(5, TimeUnit.SECONDS);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static void checkForMismatchedIDs(String table) {
    try {
      RowSet<Row> results = getRecordsWithUnmatchedIds(TENANT_ID, table);

      Integer mismatchedRowCount = results.rowCount();

      assertThat(mismatchedRowCount, is(0));

    } catch (Exception e) {
      log.error(String.format(
        "Unable to determine mismatched ID rows for '%s': '%s'",
        table, e.getMessage()), e);
      assert false;
    }
  }

  private static RowSet<Row> getRecordsWithUnmatchedIds(String tenantId, String tableName)
    throws InterruptedException, ExecutionException, TimeoutException {

    PostgresClient postgresClient = PostgresClient.getInstance(getVertx(), tenantId);

    CompletableFuture<RowSet<Row>> selectCompleted = new CompletableFuture<>();

    String sql = String.format(
      "SELECT null FROM %s_%s.%s" + " WHERE CAST(id AS VARCHAR(50)) != jsonb->>'id'",
      tenantId, "mod_circulation_storage", tableName);

    postgresClient.selectRead(sql, 0, result -> {
      if (result.succeeded()) {
        selectCompleted.complete(result.result());
      } else {
        selectCompleted.completeExceptionally(result.cause());
      }
    });

    return selectCompleted.get(5, TimeUnit.SECONDS);
  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

    vertx.deployVerticle(RestVerticle.class.getName(), options)
      .onSuccess(deploymentComplete::complete)
      .onFailure(deploymentComplete::completeExceptionally);

    deploymentComplete.get(30, TimeUnit.SECONDS);
  }

  static protected void prepareTenant(String tenantId, boolean loadSample) {
    prepareTenant(tenantId, null, "mod-circulation-storage-1.0.0", loadSample);
  }

  private static void prepareTenant(String tenantId, String moduleFrom, String moduleTo,
                                    boolean loadSample) {

    JsonArray ar = new JsonArray();
    ar.add(new JsonObject().put("key", "loadReference").put("value", "true"));
    ar.add(new JsonObject().put("key", "loadSample").put("value", Boolean.toString(loadSample)));

    JsonObject jo = new JsonObject();
    jo.put("parameters", ar);
    if (moduleFrom != null) {
      jo.put("module_from", moduleFrom);
    }
    jo.put("module_to", moduleTo);
    tenantOp(tenantId, jo);
  }

  protected static void removeTenant(String tenantId) {
    JsonObject jo = new JsonObject();
    jo.put("purge", Boolean.TRUE);
    tenantOp(tenantId, jo);
  }

  @SneakyThrows
  private static void tenantOp(String tenantId, JsonObject job) {
    CompletableFuture<JsonResponse> tenantPrepared = new CompletableFuture<>();

    OkapiHttpClient client = new OkapiHttpClient(vertx);

    client.post(storageUrl("/_/tenant"), job, tenantId,
        ResponseHandler.json(tenantPrepared));

    JsonResponse response = tenantPrepared.get(60, TimeUnit.SECONDS);

    String failureMessage = String.format("Tenant post failed: %s: %s",
        response.getStatusCode(), response);

    // wait if not complete ...
    if (response.getStatusCode() == 201) {
      String id = response.getJson().getString("id");

      tenantPrepared = new CompletableFuture<>();
      client.get(storageUrl("/_/tenant/" + id + "?wait=60000"), tenantId,
          ResponseHandler.json(tenantPrepared));
      response = tenantPrepared.get(60, TimeUnit.SECONDS);

      failureMessage = String.format("Tenant get failed: %s: %s",
          response.getStatusCode(), response.getBody());

      assertThat(failureMessage, response.getStatusCode(), is(200));
    } else {
      assertThat(failureMessage, response.getStatusCode(), is(204));
    }
  }

}
