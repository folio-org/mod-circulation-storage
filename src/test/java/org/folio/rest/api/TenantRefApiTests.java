package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
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
public class TenantRefApiTests {
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
  private static final String DEFAULT_UUID = "00000000-0000-4000-8000-000000000000";

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
  public static void beforeAll(final TestContext context) throws Exception {
    Async async = context.async();

    vertx = Vertx.vertx();
    tenantClient = new TenantClient("http://localhost:" + wireMock.port(), TENANT, TOKEN,
      WebClient.create(vertx));

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    StorageTestSuite.before();

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
          .collect(toMap(TenantRefApiTests::getId, identity()));
        // Need to reset all mocks before each test because some tests can remove stubs to mimic
        // a failure on other modules' side
        mockEndpoints();
        async.complete();
      });
  }

  @AfterClass
  public static void afterAll(final TestContext context) throws Exception {
    deleteTenant(tenantClient);
    StorageTestSuite.after();
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
        validateMigrationResult(context, async);
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

  @Test
  public void jobFailsWhenItemWasNotFound(final TestContext context) {
    Async async = context.async();
    wireMock.removeStub(itemStorageStub);
    jobFailsWhenRemoteCallFails(context, async);
  }

  private void jobFailsWhenRemoteCallFails(TestContext context, Async async) {
    postTenant(context, PREVIOUS_MODULE_VERSION, MIGRATION_MODULE_VERSION)
      .onSuccess(job -> {
        context.assertTrue(job.getError().contains("Request failed: GET"));
        context.assertTrue(job.getError().contains("Response: [404]"));
        assertThatNoRequestsWereUpdated(context);
        async.complete();
      });
  }

  @Test
  public void useDefaultValuesForInstanceIdAndHoldingsRecordIdWhenItemWasNotFound(TestContext context) {
    Async async = context.async();

    JsonObject randomRequest = requestsBeforeMigration.values()
      .stream()
      .findAny()
      .orElseThrow();

    randomRequest.put("itemId", UUID.randomUUID());
    String requestId = getId(randomRequest);

    postgresClient.update(REQUEST_TABLE_NAME, randomRequest, requestId)
      .compose(r -> postTenant(context, PREVIOUS_MODULE_VERSION, MIGRATION_MODULE_VERSION))
      .compose(job -> getRequestAsJson(requestId))
      .onFailure(context::fail)
      .onSuccess(updatedRequest -> {
        context.assertEquals(DEFAULT_UUID, updatedRequest.getString("instanceId"));
        context.assertEquals(DEFAULT_UUID, updatedRequest.getString("holdingsRecordId"));
        validateMigrationResult(context, async);
      });

  }

  @Test
  public void changesMadeForAllBatchesAreRevertedInCaseOfError(TestContext context) {
    Async async = context.async();
    wireMock.removeStub(itemStorageStub);

    // first batch - return valid response
    wireMock.stubFor(get(urlMatching(ITEM_STORAGE_URL + ANY_URL_PARAMS_REGEX_TEMPLATE))
      .atPriority(0)
      .inScenario(FAIL_SECOND_CALL_SCENARIO)
      .willReturn(ok().withBody(new JsonObject().put("items", new JsonArray(items)).encodePrettily()))
      .willSetStateTo(FIRST_CALL_MADE_SCENARIO_STATE));

    // second batch - return 500
    wireMock.stubFor(get(urlMatching(ITEM_STORAGE_URL + ANY_URL_PARAMS_REGEX_TEMPLATE))
      .atPriority(0)
      .inScenario(FAIL_SECOND_CALL_SCENARIO)
      .whenScenarioStateIs(FIRST_CALL_MADE_SCENARIO_STATE)
      .willReturn(serverError()));

    postTenant(context, PREVIOUS_MODULE_VERSION, MIGRATION_MODULE_VERSION)
      .onSuccess(job -> {
        context.assertFalse(job.getError().isEmpty());
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

  private static Future<JsonObject> getRequestAsJson(String requestId) {
    return postgresClient.getById(REQUEST_TABLE_NAME, requestId);
  }

  private void validateMigrationResult(TestContext context, Async async) {
    getAllRequestsAsJson()
      .onFailure(context::fail)
      .onSuccess(requestsAfterMigration -> {
        context.assertEquals(requestsBeforeMigration.size(), requestsAfterMigration.size());
        requestsAfterMigration.forEach(request -> validateMigrationResult(context, request));
        async.complete();
      });
  }

  private void validateMigrationResult(TestContext context, JsonObject requestAfter) {
    JsonObject requestBefore = requestsBeforeMigration.get(getId(requestAfter));

    context.assertNotNull(requestBefore);
    context.assertNotNull(requestAfter);

    context.assertEquals(requestAfter.getString("requestLevel"), "Item");
    context.assertNotNull(requestAfter.getString("instanceId"));
    context.assertNotNull(requestAfter.getString("holdingsRecordId"));

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
    InputStream tableInput = TenantRefApiTests.class.getClassLoader().getResourceAsStream(
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
