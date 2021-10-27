package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.format;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.HttpStatus.HTTP_CREATED;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

@RunWith(VertxUnitRunner.class)
public class TenantApiTest {
  protected static final String OLD_MODULE_VERSION = "13.0.0";
  protected static final String PREVIOUS_MODULE_VERSION = "13.1.0";
  protected static final String MIGRATION_MODULE_VERSION = "13.2.0";
  protected static final String NEXT_MODULE_VERSION = "13.4.0";
  protected static final String MODULE_NAME = "mod_circulation_storage";
  protected static final int PORT = NetworkUtils.nextFreePort();
  protected static final String URL = "http://localhost:" + PORT;
  protected static final String TENANT = "test_tenant";
  protected static final String REQUEST_TABLE_NAME = "request";
  protected static final String REQUEST_TABLE =
    format("%s_%s.%s", TENANT, MODULE_NAME, REQUEST_TABLE_NAME);
  protected static final String TOKEN = generateToken();
  private static final int GET_TENANT_TIMEOUT_MS = 10000;
  private static final String ITEM_STORAGE_URL = "/item-storage/items";
  private static final String HOLDINGS_STORAGE_URL = "/holdings-storage/holdings";
  private static final String ANY_URL_PARAMS_REGEX_TEMPLATE = "\\?.*";
  private static final String FAIL_SECOND_CALL_SCENARIO = "Test scenario";
  private static final String FIRST_CALL_MADE_SCENARIO_STATE = "First call made";

  private static StubMapping itemStorageStub;
  private static StubMapping holdingsStorageStub;

  private static List<JsonObject> items;
  private static List<JsonObject> holdingRecords;

  protected static Vertx vertx;
  protected static TenantClient tenantClient;
  protected static PostgresClient postgresClient;
  protected static String jobId;

  private static Map<String, JsonObject> requestsBeforeMigration = new HashMap<>();

  @ClassRule
  public static WireMockRule wireMock = new WireMockRule(
    new WireMockConfiguration().dynamicPort());

  @BeforeClass
  public static void beforeAll(final TestContext context) {
    Async async = context.async();

    vertx = Vertx.vertx();
    tenantClient = new TenantClient("http://localhost:" + wireMock.port(), TENANT, TOKEN,
      WebClient.create(vertx));

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", PORT));

    mockEndpoints();

    vertx.deployVerticle(RestVerticle.class.getName(), deploymentOptions)
      .compose(r -> postTenant(context, OLD_MODULE_VERSION, PREVIOUS_MODULE_VERSION))
      .onFailure(context::fail)
      .onSuccess(r -> {
        postgresClient = PostgresClient.getInstance(vertx, TENANT);
        async.complete();
      });
  }

  @Before
  public void beforeEach(TestContext context) throws Exception {
    Async async = context.async();

    loadRequests()
      .compose(r -> getAllRequestsAsJson())
      .onFailure(context::fail)
      .onSuccess(requests -> {
        requestsBeforeMigration = requests.stream()
          .collect(toMap(TenantApiTest::getId, identity()));
        // Need to reset all mocks before each test because some tests can remove stubs to mimic
        // a failure on other modules' side
        mockEndpoints();
        async.complete();
      });
  }

  @AfterClass
  public static void afterAll(final TestContext context) {
    deleteTenant(tenantClient);
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      PostgresClient.stopPostgresTester();
      async.complete();
    }));
  }

  @Test
  public void migrationShouldBeSkippedWhenUpgradingToLowerVersion(final TestContext context) {
    Async async = context.async();

    postTenant(context, OLD_MODULE_VERSION, PREVIOUS_MODULE_VERSION)
      .onSuccess(job -> {
        assertThatNoRequestsWereUpdated(context);
        async.complete();
      });
  }

  @Test
  public void migrationShouldBeSkippedWhenUpgradingFromAlreadyMigratedVersion(final TestContext context) {
    Async async = context.async();

    postTenant(context, MIGRATION_MODULE_VERSION, NEXT_MODULE_VERSION)
      .onSuccess(job -> {
        assertThatNoRequestsWereUpdated(context);
        async.complete();
      });
  }

  @Test
  public void jobCompletedWhenMigrationIsSuccessful(TestContext context) {
    Async async = context.async();

    postTenant(context, PREVIOUS_MODULE_VERSION, MIGRATION_MODULE_VERSION)
      .onSuccess(job -> {
        context.assertNull(job.getError());
        validateMigrationResult(context);
        async.complete();
      });
  }

  @Test
  public void jobFailsWhenItemStorageCallFails(TestContext context) {
    Async async = context.async();
    wireMock.removeStub(itemStorageStub);
    jobFailsWhenRemoteCallFails(context, async);
  }

  @Test
  public void jobFailsWhenHoldingsStorageCallFails(TestContext context) {
    Async async = context.async();
    wireMock.removeStub(holdingsStorageStub);
    jobFailsWhenRemoteCallFails(context, async);
  }

  private void jobFailsWhenRemoteCallFails(TestContext context, Async async) {
    postTenant(context, PREVIOUS_MODULE_VERSION, MIGRATION_MODULE_VERSION)
      .onSuccess(job -> {
        context.assertTrue(job.getError().contains("processing failed"));
        context.assertTrue(job.getError().contains("Response status code: 404"));
        assertThatNoRequestsWereUpdated(context);
        async.complete();
      });
  }

  @Test
  public void jobFailsWhenItemWasNotFound(final TestContext context) {
    Async async = context.async();
    wireMock.removeStub(itemStorageStub);
    jobFailsWhenFailedToFindInstanceIds(context, async, ITEM_STORAGE_URL, "items", items);
  }

  @Test
  public void jobFailsWhenHoldingsRecordWasNotFound(final TestContext context) {
    Async async = context.async();
    wireMock.removeStub(holdingsStorageStub);
    jobFailsWhenFailedToFindInstanceIds(context, async, HOLDINGS_STORAGE_URL,
      "holdingsRecords", holdingRecords);
  }

  private void jobFailsWhenFailedToFindInstanceIds(TestContext context, Async async, String url,
    String collectionName, List<JsonObject> entities) {

    // first batch - return valid response
    wireMock.stubFor(get(urlMatching(url + ANY_URL_PARAMS_REGEX_TEMPLATE))
      .atPriority(0)
      .inScenario(FAIL_SECOND_CALL_SCENARIO)
      .willReturn(ok().withBody(new JsonObject().put(collectionName, new JsonArray(entities)).encode()))
      .willSetStateTo(FIRST_CALL_MADE_SCENARIO_STATE));

    // second batch - return empty response
    wireMock.stubFor(get(urlMatching(url + ANY_URL_PARAMS_REGEX_TEMPLATE))
      .atPriority(0)
      .inScenario(FAIL_SECOND_CALL_SCENARIO)
      .whenScenarioStateIs(FIRST_CALL_MADE_SCENARIO_STATE)
      .willReturn(ok().withBody(new JsonObject().put(collectionName, new JsonArray()).encode())));

    postTenant(context, PREVIOUS_MODULE_VERSION, MIGRATION_MODULE_VERSION)
      .onSuccess(job -> {
        context.assertTrue(job.getError().contains("processing failed: failed to find instance IDs"));
        // make sure that changes for all batches were reverted
        assertThatNoRequestsWereUpdated(context);
        async.complete();
      });
  }

  @Test
  public void jobFailsWhenRequestAlreadyHasTitleLevelRequestField(TestContext context) {
    Async async = context.async();

    JsonObject randomRequest = requestsBeforeMigration.values()
      .stream()
      .findAny()
      .orElseThrow();

    randomRequest.put("requestLevel", "item");

    jobFailsWhenRequestValidationFails(context, async, randomRequest,
      "request already contains TLR fields: " + getId(randomRequest));
  }

  @Test
  public void jobFailsWhenRequestDoesNotHaveRequiredItemLevelRequestField(TestContext context) {
    Async async = context.async();

    JsonObject randomRequest = requestsBeforeMigration.values()
      .stream()
      .findAny()
      .orElseThrow();

    randomRequest.remove("itemId");

    jobFailsWhenRequestValidationFails(context, async, randomRequest,
      "request does not contain required ILR fields: " + getId(randomRequest));
  }

  private void jobFailsWhenRequestValidationFails(TestContext context, Async async,
    JsonObject request, String expectedErrorMessage) {

    postgresClient.update(REQUEST_TABLE_NAME, request, getId(request))
      .compose(r -> postTenant(context, PREVIOUS_MODULE_VERSION, MIGRATION_MODULE_VERSION))
      .onFailure(context::fail)
      .onSuccess(job -> {
        context.assertTrue(job.getError().contains(expectedErrorMessage));
        assertThatNoRequestsWereUpdated(context);
        async.complete();
      });
  }

  private static Future<TenantJob> postTenant(TestContext context, String fromVersion,
    String toVersion) {

    return tenantClient.postTenant(getTenantAttributes(fromVersion, toVersion))
      .onSuccess(response -> context.assertEquals(response.statusCode(), HTTP_CREATED.toInt()))
      .map(response -> response.bodyAsJson(TenantJob.class))
      .compose(job -> tenantClient.getTenantByOperationId(job.getId(), GET_TENANT_TIMEOUT_MS))
      .map(response -> response.bodyAsJson(TenantJob.class))
      .onSuccess(job -> context.assertTrue(job.getComplete()))
      .onFailure(context::fail);
  }

  private static void assertThatNoRequestsWereUpdated(TestContext context) {
    postgresClient.select("SELECT COUNT(*) " +
        "FROM " + REQUEST_TABLE + " " +
        "WHERE jsonb->>'requestLevel' IS NOT null")
      .onFailure(context::fail)
      .onSuccess(rowSet -> context.assertEquals(0, getCount(rowSet)));
  }

  private static Future<List<JsonObject>> getAllRequestsAsJson() {
    return postgresClient.select("SELECT * FROM " + REQUEST_TABLE)
      .map(rowSet -> StreamSupport.stream(rowSet.spliterator(), false)
        .map(row -> row.getJsonObject("jsonb"))
        .collect(toList()));
  }

  private void validateMigrationResult(TestContext context) {
    getAllRequestsAsJson()
      .onFailure(context::fail)
      .onSuccess(requestsAfterMigration -> {
        context.assertEquals(requestsBeforeMigration.size(), requestsAfterMigration.size());
        requestsAfterMigration.forEach(request -> validateMigrationResult(context, request));
      });
  }

  private void validateMigrationResult(TestContext context, JsonObject requestAfter) {
    JsonObject requestBefore = requestsBeforeMigration.get(getId(requestAfter));

    context.assertNotNull(requestBefore);
    context.assertNotNull(requestAfter);

    context.assertEquals(requestAfter.getString("requestLevel"), "item");
    context.assertNotNull(requestAfter.getString("instanceId"));

    if (requestBefore.containsKey("item")) {
      JsonObject itemBefore = requestBefore.getJsonObject("item");
      JsonObject itemAfter = requestAfter.getJsonObject("item");
      JsonObject instance = requestAfter.getJsonObject("instance");

      if (itemBefore.containsKey("title")) {
        context.assertEquals(instance.getString("title"), itemBefore.getString("title"));
      }

      if (itemBefore.containsKey("identifiers")) {
        context.assertEquals(instance.getJsonArray("identifiers"), itemBefore.getJsonArray("identifiers"));
      }

      context.assertFalse(itemAfter.containsKey("title"));
      context.assertFalse(itemAfter.containsKey("identifiers"));
    }
  }

  static void deleteTenant(TenantClient tenantClient) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    tenantClient.deleteTenantByOperationId(jobId, deleted -> {
      if (deleted.failed()) {
        future.completeExceptionally(new RuntimeException("Failed to delete tenant"));
        return;
      }
      future.complete(null);
    });
  }

  private static void mockEndpoints() {
    items = new ArrayList<>();
    holdingRecords = new ArrayList<>();

    requestsBeforeMigration.values()
      .forEach(request -> {
        final String holdingsRecordId = randomId();

        items.add(new JsonObject()
          .put("id", request.getString("itemId"))
          .put("holdingsRecordId", holdingsRecordId));

        holdingRecords.add(new JsonObject()
          .put("id", holdingsRecordId)
          .put("instanceId", randomId()));
      });

    String itemsResponse = new JsonObject()
      .put("items", new JsonArray(items))
      .encodePrettily();

    String holdingRecordsResponse = new JsonObject()
      .put("holdingsRecords", new JsonArray(holdingRecords))
      .encodePrettily();

    wireMock.resetAll();

    wireMock.stubFor(post(urlEqualTo("/pubsub/event-types"))
      .willReturn(created()));

    wireMock.stubFor(post(urlEqualTo("/pubsub/event-types?"))
      .willReturn(created()));

    wireMock.stubFor(post(urlMatching("/pubsub/event-types/declare/(publisher|subscriber)"))
      .willReturn(created()));

    itemStorageStub = wireMock.stubFor(
      get(urlMatching(ITEM_STORAGE_URL + ANY_URL_PARAMS_REGEX_TEMPLATE))
        .willReturn(ok().withBody(itemsResponse)));

    holdingsStorageStub = wireMock.stubFor(
      get(urlMatching(HOLDINGS_STORAGE_URL + ANY_URL_PARAMS_REGEX_TEMPLATE))
        .willReturn(ok().withBody(holdingRecordsResponse)));

    wireMock.stubFor(any(anyUrl())
      .atPriority(Integer.MAX_VALUE)
      .willReturn(aResponse().proxiedFrom(URL)));
  }

  protected static TenantAttributes getTenantAttributes(String moduleFrom, String moduleTo) {
    final Parameter loadReferenceParameter = new Parameter()
      .withKey("loadReference").withValue("true");

    return new TenantAttributes()
      .withModuleFrom(format("%s-%s", MODULE_NAME, moduleFrom))
      .withModuleTo(format("%s-%s", MODULE_NAME, moduleTo))
      .withParameters(Collections.singletonList(loadReferenceParameter));
  }

  protected static Future<List<String>> loadRequests() throws Exception {
    InputStream tableInput = TenantApiTest.class.getClassLoader().getResourceAsStream(
      "mocks/TlrDataMigrationTestData.sql");
    String sqlFile = IOUtils.toString(Objects.requireNonNull(tableInput), StandardCharsets.UTF_8);
    return postgresClient.runSQLFile(sqlFile, true);
  }

  private static String generateToken() {
    final String payload = new JsonObject()
      .put("user_id", randomId())
      .put("tenant", TENANT)
      .put("sub", "admin")
      .toString();

    return format("1.%s.3", Base64.getEncoder()
      .encodeToString(payload.getBytes()));
  }

  protected static String randomId() {
    return UUID.randomUUID().toString();
  }

  private static int getCount(RowSet<Row> rowSet) {
    return rowSet.iterator()
      .next()
      .get(Integer.class, 0);
  }

  private static String getId(JsonObject jsonObject) {
    return jsonObject.getString("id");
  }
}
