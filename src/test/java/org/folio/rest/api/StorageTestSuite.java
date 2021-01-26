package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.folio.support.EventType.LOG_RECORD;
import static org.folio.support.MockServer.getCreatedEventTypes;
import static org.folio.support.MockServer.getRegisteredPublishers;
import static org.folio.support.MockServer.getRegisteredSubscribers;
import static org.folio.util.pubsub.PubSubClientUtils.constructModuleName;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

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
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.github.tomakehurst.wiremock.WireMockServer;

import org.folio.rest.RestVerticle;
import org.folio.rest.api.loans.LoansAnonymizationApiTest;
import org.folio.rest.api.migration.StaffSlipsMigrationScriptTest;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.OkapiHttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.support.MockServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  AnonymizeLoansApiTest.class,
  LoansApiTest.class,
  LoansAnonymizationApiTest.class,
  CirculationRulesApiTest.class,
  FixedDueDateApiTest.class,
  LoanPoliciesApiTest.class,
  RequestPreferencesApiTest.class,
  RequestsApiTest.class,
  LoansApiHistoryTest.class,
  StaffSlipsApiTest.class,
  CancellationReasonsApiTest.class,
  PatronNoticePoliciesApiTest.class,
  RequestPoliciesApiTest.class,
  RequestExpirationApiTest.class,
  ScheduledNoticesAPITest.class,
  PatronActionSessionAPITest.class,
  RequestBatchAPITest.class,
  CheckInStorageApiTest.class,
  StaffSlipsMigrationScriptTest.class
})
public class StorageTestSuite {

  private static final Logger log = LogManager.getLogger();

  public static final String TENANT_ID = "test_tenant";

  private static Vertx vertx;
  public static final int VERTICLE_PORT = NetworkUtils.nextFreePort();
  public static final int PROXY_PORT = NetworkUtils.nextFreePort();
  public static final int OKAPI_MOCK_PORT = NetworkUtils.nextFreePort();
  private static boolean initialised = false;
  private static MockServer mockServer;
  private static final WireMockServer wireMockServer = new WireMockServer(PROXY_PORT);

  /**
   * Return a URL for the path and the parameters.
   *
   * <p>Example: storageUrl("/foo", "year", "2019", "name", "A & Co") may return an URL for
   * http://localhost:46131/foo?year=2019&name=A%20%26%20Co
   */
  public static URL storageUrl(String path, String ... parameterKeyValue) throws MalformedURLException {
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

  @BeforeClass
  public static void before()
    throws IOException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    vertx = Vertx.vertx();

    String useExternalDatabase = System.getProperty(
      "org.folio.circulation.storage.test.database",
      "embedded");

    switch (useExternalDatabase) {
      case "environment":
        log.info("Using environment settings");
        break;

      case "external":
        String postgresConfigPath = System.getProperty(
          "org.folio.circulation.storage.test.config",
          "/postgres-conf-local.json");

        log.info(String.format(
          "Using external configuration settings: '%s'", postgresConfigPath));

        PostgresClient.setConfigFilePath(postgresConfigPath);
        break;

      case "embedded":
        log.info("Using embedded PostgreSQL");

        PostgresClient.setIsEmbedded(true);
        PostgresClient.setEmbeddedPort(NetworkUtils.nextFreePort());

        PostgresClient client = PostgresClient.getInstance(vertx);
        client.startEmbeddedPostgres();
        break;

      default:
        String message = "No understood database choice made."
          + "Please set org.folio.circulation.storage.test.config"
          + "to 'external', 'environment' or 'embedded'";

        log.error(message);
        assert false;
    }

    DeploymentOptions options = new DeploymentOptions();
    options.setConfig(new JsonObject().put("http.port", VERTICLE_PORT));
    startVerticle(options);

    mockServer = new MockServer(OKAPI_MOCK_PORT, vertx);
    mockServer.start();

    wireMockServer.start();

    wireMockServer.stubFor(post(urlMatching("/pubsub/.*"))
      .atPriority(1)
      .willReturn(aResponse().proxiedFrom("http://localhost:" + OKAPI_MOCK_PORT)));

    wireMockServer.stubFor(any(anyUrl())
      .atPriority(10)
      .willReturn(aResponse().proxiedFrom("http://localhost:" + VERTICLE_PORT)));

    prepareTenant(TENANT_ID, true);

    initialised = true;
  }

  @AfterClass
  public static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    initialised = false;

    removeTenant(TENANT_ID);

    mockServer.close();

    CompletableFuture<String> undeploymentComplete = new CompletableFuture<>();

    vertx.close(res -> {
      if (res.succeeded()) {
        undeploymentComplete.complete(null);
      } else {
        undeploymentComplete.completeExceptionally(res.cause());
      }
    });

    undeploymentComplete.get(20, TimeUnit.SECONDS);

    PostgresClient.stopEmbeddedPostgres();
  }

  public static boolean isNotInitialised() {
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

    postgresClient.select(sql, result -> {
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

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      if (res.succeeded()) {
        deploymentComplete.complete(res.result());
      } else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });

    deploymentComplete.get(30, TimeUnit.SECONDS);
  }

/*  private static void prepareTenant(String tenantId) {
    CompletableFuture<TextResponse> tenantPrepared = new CompletableFuture<>();

    log.info("Making request to prepare tenant in module");

    try {
      OkapiHttpClient client = new OkapiHttpClient(vertx);

      JsonArray ar = new JsonArray();

      ar.add(new JsonObject().put("key", "loadReference").put("value", "true"));
      ar.add(new JsonObject().put("key", "loadSample").put("value", "true"));
      JsonObject jo = new JsonObject();
      jo.put("parameters", ar);
      jo.put("module_to", "mod-circulation-storage-1.0.0");

      client.post(storageUrl("/_/tenant"), jo, tenantId, null,
        ResponseHandler.text(tenantPrepared));

      TextResponse response = tenantPrepared.get(20, TimeUnit.SECONDS);

      String failureMessage = String.format("Tenant preparation failed: %s: %s",
        response.getStatusCode(), response.getBody());

      assertThat(failureMessage, response.getStatusCode(), is(201));

      List<JsonObject> eventTypes = getCreatedEventTypes();
      assertThat(eventTypes, hasSize(1));
      EventDescriptor descriptor = eventTypes.get(0).mapTo(EventDescriptor.class);
      assertThat(descriptor.getEventType(), equalTo(LOG_RECORD.name()));

      List<JsonObject> publishers = getRegisteredPublishers();
      assertThat(publishers, hasSize(1));
      PublisherDescriptor publisher = publishers.get(0).mapTo(PublisherDescriptor.class);
      assertThat(publisher.getModuleId(), equalTo(constructModuleName()));

      assertThat(publisher.getEventDescriptors(), hasSize(1));
      assertThat(publisher.getEventDescriptors().get(0).getEventType(), equalTo(LOG_RECORD.name()));

      List<JsonObject> subscribers = getRegisteredSubscribers();
      assertThat(subscribers, hasSize(0));

    } catch (Exception e) {
      log.error("Tenant preparation failed: " + e.getMessage(), e);
      assert false;
    }
  }*/

/*  private static void removeTenant(String tenantId) {
    CompletableFuture<TextResponse> tenantDeleted = new CompletableFuture<>();

    log.info("Making request to clean up tenant in module");

    try {
      OkapiHttpClient client = new OkapiHttpClient(vertx);

      client.delete(storageUrl("/_/tenant"), tenantId,
        ResponseHandler.text(tenantDeleted));

      TextResponse response = tenantDeleted.get(10, TimeUnit.SECONDS);

      String failureMessage = String.format("Tenant clean up failed: %s: %s",
        response.getStatusCode(), response.getBody());

      assertThat(failureMessage, response.getStatusCode(), is(204));

    } catch (Exception e) {
      log.error("Tenant clean up failed: " + e.getMessage(), e);
      assert false;
    }
  }*/

  static private void prepareTenant(String tenantId, boolean loadSample) {
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

  private static void removeTenant(String tenantId) {

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
