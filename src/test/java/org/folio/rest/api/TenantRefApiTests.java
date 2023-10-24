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
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_CANCELLED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_FILLED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_UNFILLED;

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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.IOUtils;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Request;
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
  protected static final String TLR_MIGRATION_OLD_MODULE_VERSION = "13.0.0";
  protected static final String TLR_MIGRATION_PREV_MODULE_VERSION = "13.1.0";
  protected static final String TLR_MIGRATION_MODULE_VERSION = "14.0.0";
  protected static final String TLR_MIGRATION_NEXT_MODULE_VERSION = "14.1.0";
  protected static final String REQ_SEARCH_MIGRATION_OLD_MOD_VER = "15.0.0";
  protected static final String REQ_SEARCH_MIGRATION_PREV_MOD_VER = "16.0.0";
  protected static final String REQ_SEARCH_MIGRATION_MOD_VER = "16.1.0";
  protected static final String REQ_SEARCH_MIGRATION_NEXT_MOD_VER = "16.2.0";
  protected static final String REQ_FULFILLMENT_PREFERENCE_SPELLING_OLD_VER = "17.1.1";
  protected static final String REQ_FULFILLMENT_PREFERENCE_SPELLING_NEW_VER = "17.1.2";
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
  private static final String SERVICE_PINTS_STORAGE_URL = "/service-points";
  private static final String ANY_URL_PARAMS_REGEX_TEMPLATE = "\\?.*";
  private static final String FAIL_SECOND_CALL_SCENARIO = "Test scenario";
  private static final String FIRST_CALL_MADE_SCENARIO_STATE = "First call made";
  private static final String DEFAULT_UUID = "00000000-0000-4000-8000-000000000000";
  private static final String REQUEST_ID_MISSING_HOLDINGS_RECORD_ID =
    "6110e6ab-1d84-4bc5-a88e-e984acca9744";
  private static final String REQUEST_ID_MISSING_EFFECTIVE_SHELVING_ORDER =
    "45496a79-e1f3-412e-9076-fc5c4ee893f9";
  private static final String REQUEST_ID_MISSING_EFFECTIVE_CALL_NUMBER_COMPONENTS =
    "ddcee62b-c41f-4036-a1d7-5a039d259e87";
  private static final String REQUEST_ID_MISSING_CALL_NUMBER =
    "f999719d-ca7e-44ce-9f75-9071c856e5d7";
  private static final String REQUEST_ID_MISSING_PREFIX = "6ad37eb7-591b-46f3-9902-c1992ca41158";
  private static final String REQUEST_ID_MISSING_SUFFIX = "ecd86aab-a0ac-4d3c-bb90-0df390b1c6c4";
  private static final String REQUEST_ID_MISSING_PICKUP_SERVICE_POINT_NAME =
    "87a7dfd9-8fdb-4b0d-9529-14912b484860";

  private static StubMapping itemStorageStub;
  private static StubMapping holdingsStorageStub;
  private static StubMapping servicePointsStorageStub;

  private static List<JsonObject> items;
  private static List<JsonObject> holdingRecords;
  private static List<JsonObject> servicePoints;

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
      .compose(r -> postTenant(context, TLR_MIGRATION_OLD_MODULE_VERSION, TLR_MIGRATION_PREV_MODULE_VERSION))
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
  public void tlrMigrationShouldBeSkippedWhenUpgradingToLowerVersion(final TestContext context) {
    Async async = context.async();

    postTenant(context, TLR_MIGRATION_OLD_MODULE_VERSION, TLR_MIGRATION_PREV_MODULE_VERSION)
      .onSuccess(job -> {
        assertThatNoRequestsWereUpdatedByTlrMigration(context);
        async.complete();
      });
  }

  @Test
  public void tlrMigrationShouldBeSkippedWhenUpgradingFromAlreadyMigratedVersion(
    final TestContext context) {

    Async async = context.async();

    postTenant(context, TLR_MIGRATION_MODULE_VERSION, TLR_MIGRATION_NEXT_MODULE_VERSION)
      .onSuccess(job -> {
        assertThatNoRequestsWereUpdatedByTlrMigration(context);
        async.complete();
      });
  }

  @Test
  public void jobCompletedWhenTlrMigrationIsSuccessful(TestContext context) {
    Async async = context.async();

    postTenant(context, TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION)
      .onSuccess(job -> {
        context.assertNull(job.getError());
        validateTlrMigrationResult(context, async);
      });
  }


  @Test
  public void requestSearchMigrationShouldBeSkippedWhenUpgradingToLowerVersion(final TestContext context) {
    Async async = context.async();

    postTenant(context, REQ_SEARCH_MIGRATION_OLD_MOD_VER, REQ_SEARCH_MIGRATION_PREV_MOD_VER)
      .onSuccess(job -> {
        assertThatNoRequestsWereUpdatedByRequestSearchMigration(context);
        async.complete();
      });
  }

  @Test
  public void requestSearchMigrationShouldBeSkippedWhenUpgradingFromAlreadyMigratedVersion(
    final TestContext context) {

    Async async = context.async();

    postTenant(context, REQ_SEARCH_MIGRATION_MOD_VER, REQ_SEARCH_MIGRATION_NEXT_MOD_VER)
      .onSuccess(job -> {
        assertThatNoRequestsWereUpdatedByRequestSearchMigration(context);
        async.complete();
      });
  }

  @Test
  public void jobCompletedWhenRequestSearchMigrationIsSuccessful(TestContext context) {
    Async async = context.async();

    postTenant(context, REQ_SEARCH_MIGRATION_PREV_MOD_VER, REQ_SEARCH_MIGRATION_MOD_VER)
      .onSuccess(job -> {
        context.assertNull(job.getError());
        validateRequestSearchMigrationResult(context, async);
      });
  }

  @Test
  public void tlrMigrationFailsWhenItemStorageCallFails(TestContext context) {
    Async async = context.async();
    wireMock.removeStub(itemStorageStub);
    tlrMigrationFailsWhenRemoteCallFails(context, async);
  }

  @Test
  public void tlrMigrationFailsWhenHoldingsStorageCallFails(TestContext context) {
    Async async = context.async();
    wireMock.removeStub(holdingsStorageStub);
    tlrMigrationFailsWhenRemoteCallFails(context, async);
  }

  @Test
  public void tlrMigrationFailsWhenItemWasNotFound(final TestContext context) {
    Async async = context.async();
    wireMock.removeStub(itemStorageStub);
    tlrMigrationFailsWhenRemoteCallFails(context, async);
  }

  private void tlrMigrationFailsWhenRemoteCallFails(TestContext context, Async async) {
    postTenant(context, TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION)
      .onSuccess(job -> {
        context.assertTrue(job.getError().contains("Request failed: GET"));
        context.assertTrue(job.getError().contains("Response: [404]"));
        assertThatNoRequestsWereUpdatedByTlrMigration(context);
        async.complete();
      });
  }

  @Test
  public void requestSearchMigrationFailsWhenItemStorageCallFails(TestContext context) {
    Async async = context.async();
    wireMock.removeStub(itemStorageStub);
    requestSearchMigrationFailsWhenRemoteCallFails(context, async);
  }

  @Test
  public void requestSearchMigrationFailsWhenServicePointsStorageCallFails(TestContext context) {
    Async async = context.async();
    wireMock.removeStub(servicePointsStorageStub);
    requestSearchMigrationFailsWhenRemoteCallFails(context, async);
  }

  private void requestSearchMigrationFailsWhenRemoteCallFails(TestContext context, Async async) {
    postTenant(context, REQ_SEARCH_MIGRATION_PREV_MOD_VER, REQ_SEARCH_MIGRATION_MOD_VER)
      .onSuccess(job -> {
        context.assertTrue(job.getError().contains("Request failed: GET"));
        context.assertTrue(job.getError().contains("Response: [404]"));
        assertThatNoRequestsWereUpdatedByRequestSearchMigration(context);
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
      .compose(r -> postTenant(context, TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION))
      .compose(job -> getRequestAsJson(requestId))
      .onFailure(context::fail)
      .onSuccess(updatedRequest -> {
        context.assertEquals(DEFAULT_UUID, updatedRequest.getString("instanceId"));
        context.assertEquals(DEFAULT_UUID, updatedRequest.getString("holdingsRecordId"));
        validateTlrMigrationResult(context, async);
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

    postTenant(context, TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION)
      .onSuccess(job -> {
        context.assertFalse(job.getError().isEmpty());
        assertThatNoRequestsWereUpdatedByTlrMigration(context);
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

  @Test
  public void migrationRemovesPositionFromClosedRequests(TestContext context) {
    Async async = context.async();

    List<String> closedStatuses = Stream.of(CLOSED_FILLED, CLOSED_UNFILLED, CLOSED_PICKUP_EXPIRED,
        CLOSED_CANCELLED)
      .map(Request.Status::value)
      .collect(toList());

    postTenant(context, TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION)
      .compose(job -> getAllRequestsAsJson())
      .onFailure(context::fail)
      .onSuccess(requestsAfterMigration -> {
        requestsAfterMigration.forEach(request -> {
          Integer position = request.getInteger("position");
          if (closedStatuses.contains(request.getString("status"))) {
            context.assertNull(position);
          } else {
            context.assertNotNull(position);
          }
          async.complete();
        });
      });
  }

  @Test
  public void migrationCorrectsFulfillmentPreferenceSpelling(TestContext context) {
    Async async = context.async();

    postTenant(context, REQ_FULFILLMENT_PREFERENCE_SPELLING_OLD_VER,
      REQ_FULFILLMENT_PREFERENCE_SPELLING_NEW_VER)
      .compose(job -> getAllRequestsAsJson())
      .onFailure(context::fail)
      .onSuccess(requestsAfterMigration -> {
        requestsAfterMigration.forEach(request -> {
          context.assertNull(request.getString("fulfilmentPreference"));
          context.assertNotNull(request.getString("fulfillmentPreference"));
          async.complete();
        });
      });
  }

  private void jobFailsWhenRequestValidationFails(TestContext context, Async async,
    JsonObject request, String expectedErrorMessage) {

    postgresClient.update(REQUEST_TABLE_NAME, request, getId(request))
      .compose(r -> postTenant(context, TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION))
      .onFailure(context::fail)
      .onSuccess(job -> {
        context.assertTrue(job.getError().contains(expectedErrorMessage));
        assertThatNoRequestsWereUpdatedByTlrMigration(context);
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

  private static void assertThatNoRequestsWereUpdatedByTlrMigration(TestContext context) {
    selectRead("SELECT COUNT(*) " +
        "FROM " + REQUEST_TABLE + " " +
        "WHERE jsonb->>'requestLevel' IS NOT null")
      .onFailure(context::fail)
      .onSuccess(rowSet -> context.assertEquals(0, getCount(rowSet)));
  }

  private static void assertThatNoRequestsWereUpdatedByRequestSearchMigration(TestContext context) {
    selectRead("SELECT COUNT(*) " +
        "FROM " + REQUEST_TABLE + " " +
        "WHERE jsonb->>'searchIndex' IS NOT null")
      .onFailure(context::fail)
      .onSuccess(rowSet -> context.assertEquals(0, getCount(rowSet)));
  }

  private static Future<List<JsonObject>> getAllRequestsAsJson() {
    return selectRead("SELECT * FROM " + REQUEST_TABLE)
      .map(rowSet -> StreamSupport.stream(rowSet.spliterator(), false)
        .map(row -> row.getJsonObject("jsonb"))
        .collect(toList()));
  }

  private static Future<RowSet<Row>> selectRead(String sql) {
    return Future.future(promise -> postgresClient.selectRead(sql, 0, promise));
  }

  private static Future<JsonObject> getRequestAsJson(String requestId) {
    return postgresClient.getById(REQUEST_TABLE_NAME, requestId);
  }

  private void validateTlrMigrationResult(TestContext context, Async async) {
    getAllRequestsAsJson()
      .onFailure(context::fail)
      .onSuccess(requestsAfterMigration -> {
        context.assertEquals(requestsBeforeMigration.size(), requestsAfterMigration.size());
        requestsAfterMigration.forEach(request -> validateTlrMigrationResult(context, request));
        async.complete();
      });
  }

  private void validateTlrMigrationResult(TestContext context, JsonObject requestAfter) {
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

  private void validateRequestSearchMigrationResult(TestContext context, Async async) {
    getAllRequestsAsJson()
      .onFailure(context::fail)
      .onSuccess(requestsAfterMigration -> {
        context.assertEquals(requestsBeforeMigration.size(), requestsAfterMigration.size());
        requestsAfterMigration.forEach(request -> validateRequestSearchMigrationResult(context, request));
        async.complete();
      });
  }

  private void validateRequestSearchMigrationResult(TestContext context, JsonObject requestAfter) {
    JsonObject requestBefore = requestsBeforeMigration.get(getId(requestAfter));
    String requestId = requestBefore.getString("id");

    context.assertNotNull(requestBefore);
    context.assertNotNull(requestAfter);

    if (requestBefore.containsKey("pickupServicePointId")) {
      if (!REQUEST_ID_MISSING_PICKUP_SERVICE_POINT_NAME.equals(requestId)) {
        context.assertEquals("testSpName", requestAfter.getJsonObject("searchIndex")
          .getString("pickupServicePointName"));
      }
    }

    if (!REQUEST_ID_MISSING_EFFECTIVE_SHELVING_ORDER.equals(requestId)) {
      context.assertEquals("testShelvingOrder", requestAfter.getJsonObject("searchIndex")
        .getString("shelvingOrder"));
    }

    if (!REQUEST_ID_MISSING_EFFECTIVE_CALL_NUMBER_COMPONENTS.equals(requestId) &&
      !REQUEST_ID_MISSING_CALL_NUMBER.equals(requestId)) {

      context.assertEquals("testCallNumber", requestAfter.getJsonObject("searchIndex")
        .getJsonObject("callNumberComponents").getString("callNumber"));
    }

    if (!REQUEST_ID_MISSING_EFFECTIVE_CALL_NUMBER_COMPONENTS.equals(requestId) &&
      !REQUEST_ID_MISSING_PREFIX.equals(requestId)) {

      context.assertEquals("testPrefix", requestAfter.getJsonObject("searchIndex")
        .getJsonObject("callNumberComponents").getString("prefix"));
    }

    if (!REQUEST_ID_MISSING_EFFECTIVE_CALL_NUMBER_COMPONENTS.equals(requestId) &&
      !REQUEST_ID_MISSING_SUFFIX.equals(requestId)) {

      context.assertEquals("testSuffix", requestAfter.getJsonObject("searchIndex")
        .getJsonObject("callNumberComponents").getString("suffix"));
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
    servicePoints = new ArrayList<>();

    requestsBeforeMigration.values()
      .forEach(request -> {
        final String requestId = request.getString("id");
        final String holdingsRecordId = randomId();
        final String servicePointId = request.getString("pickupServicePointId");

        JsonObject item = new JsonObject()
          .put("id", request.getString("itemId"))
          .put("holdingsRecordId", holdingsRecordId)
          .put("effectiveShelvingOrder", "testShelvingOrder")
          .put("effectiveCallNumberComponents", new JsonObject()
            .put("callNumber", "testCallNumber")
            .put("prefix", "testPrefix")
            .put("suffix", "testSuffix")
          );
        items.add(item);

        if (REQUEST_ID_MISSING_HOLDINGS_RECORD_ID.equals(requestId)) {
          item.remove("holdingsRecordId");
        }
        if (REQUEST_ID_MISSING_EFFECTIVE_SHELVING_ORDER.equals(requestId)) {
          item.remove("effectiveShelvingOrder");
        }
        if (REQUEST_ID_MISSING_EFFECTIVE_CALL_NUMBER_COMPONENTS.equals(requestId)) {
          item.remove("effectiveCallNumberComponents");
        }
        if (REQUEST_ID_MISSING_CALL_NUMBER.equals(requestId)) {
          item.getJsonObject("effectiveCallNumberComponents").remove("callNumber");
        }
        if (REQUEST_ID_MISSING_PREFIX.equals(requestId)) {
          item.getJsonObject("effectiveCallNumberComponents").remove("prefix");
        }
        if (REQUEST_ID_MISSING_SUFFIX.equals(requestId)) {
          item.getJsonObject("effectiveCallNumberComponents").remove("suffix");
        }

        holdingRecords.add(new JsonObject()
          .put("id", holdingsRecordId)
          .put("instanceId", randomId()));

        if (servicePointId != null) {
          boolean servicePointDoesNotExists = servicePoints.stream()
            .noneMatch(sp -> sp.getString("id").equals(servicePointId));
          if (servicePointDoesNotExists) {
            JsonObject servicePoint = new JsonObject()
              .put("id", servicePointId)
              .put("name", "testSpName");
            servicePoints.add(servicePoint);
            if ("87a7dfd9-8fdb-4b0d-9529-14912b484860".equals(request.getString("id"))) {
              // request with pickup service point missing a name
              servicePoint.remove("name");
            }
          }
        }
      });

    String itemsResponse = new JsonObject()
      .put("items", new JsonArray(items))
      .encodePrettily();

    String holdingRecordsResponse = new JsonObject()
      .put("holdingsRecords", new JsonArray(holdingRecords))
      .encodePrettily();

    String servicePointsResponse = new JsonObject()
      .put("servicepoints", new JsonArray(servicePoints))
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

    servicePointsStorageStub = wireMock.stubFor(
      get(urlMatching(SERVICE_PINTS_STORAGE_URL + ANY_URL_PARAMS_REGEX_TEMPLATE))
        .willReturn(ok().withBody(servicePointsResponse)));

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
