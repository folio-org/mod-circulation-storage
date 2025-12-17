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
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_CANCELLED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_FILLED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_UNFILLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

@ExtendWith(VertxExtension.class)
public class TenantRefApiTests {
  protected static final String TLR_MIGRATION_OLD_MODULE_VERSION = "13.0.0";
  protected static final String TLR_MIGRATION_PREV_MODULE_VERSION = "13.1.0";
  protected static final String TLR_MIGRATION_MODULE_VERSION = "14.0.0";
  protected static final String TLR_MIGRATION_NEXT_MODULE_VERSION = "14.1.0";
  protected static final String REQ_SEARCH_MIGRATION_OLD_MOD_VER = "15.0.0";
  protected static final String REQ_SEARCH_MIGRATION_PREV_MOD_VER = "16.0.0";
  protected static final String REQ_SEARCH_MIGRATION_MOD_VER = "16.1.0";
  protected static final String REQ_SEARCH_MIGRATION_NEXT_MOD_VER = "16.2.0";
  protected static final String REQ_FULFILLMENT_PREFERENCE_SPELLING_PREV_VER = "17.1.1";
  protected static final String REQ_FULFILLMENT_PREFERENCE_SPELLING_VER = "17.1.2";
  protected static final String REQ_FULFILLMENT_PREFERENCE_SPELLING_NEXT_VER = "17.2.0";
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
  private static final String OTHER_CANCELLATION_REASON_ID = "b548b182-55c2-4741-b169-616d9cd995a8";

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

  @RegisterExtension
  static WireMockExtension wireMock = WireMockExtension.newInstance()
    .options(new WireMockConfiguration().dynamicPort())
    .build();

  @BeforeAll
  public static void beforeAll(VertxTestContext context) throws Exception {

    vertx = Vertx.vertx();
    tenantClient = new TenantClient("http://localhost:" + wireMock.getPort(), TENANT, TOKEN,
      WebClient.create(vertx));

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    StorageTestSuite.before();

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", PORT));

    mockEndpoints();

    vertx.deployVerticle(RestVerticle.class.getName(), deploymentOptions)
      .compose(r -> postTenant(TLR_MIGRATION_OLD_MODULE_VERSION, TLR_MIGRATION_PREV_MODULE_VERSION))
      .onComplete(context.succeeding(job -> {
        postgresClient = PostgresClient.getInstance(vertx, TENANT);
        context.completeNow();
      }));
  }

  @BeforeEach
  public void beforeEach(VertxTestContext context) throws Exception {
    loadRequests()
      .compose(r -> getAllRequestsAsJson())
      .onComplete(context.succeeding(requests -> {
        requestsBeforeMigration = requests.stream()
          .collect(toMap(TenantRefApiTests::getId, identity()));
        mockEndpoints();
        context.completeNow();
      }));
  }

  @AfterAll
  public static void afterAll(VertxTestContext context) throws Exception {
    deleteTenant(tenantClient);
    StorageTestSuite.after();
    vertx.close()
      .onFailure(context::failNow)
      .onSuccess(ignored -> {
        PostgresClient.stopPostgresTester();
        context.completeNow();
      });
  }

  @Test
  public void tlrMigrationShouldBeSkippedWhenUpgradingToLowerVersion(VertxTestContext context) {
    postTenant(TLR_MIGRATION_OLD_MODULE_VERSION, TLR_MIGRATION_PREV_MODULE_VERSION)
      .onComplete(context.succeeding(job -> {
        assertThatNoRequestsWereUpdatedByMigration(context, "requestLevel");
        context.completeNow();
      }));
  }

  @Test
  public void tlrMigrationShouldBeSkippedWhenUpgradingFromAlreadyMigratedVersion(
    VertxTestContext context) {

    postTenant(TLR_MIGRATION_MODULE_VERSION, TLR_MIGRATION_NEXT_MODULE_VERSION)
      .onComplete(context.succeeding(job -> {
        assertThatNoRequestsWereUpdatedByMigration(context, "requestLevel");
        context.completeNow();
      }));
  }

  @Test
  public void jobCompletedWhenTlrMigrationIsSuccessful(VertxTestContext context) {
    postTenant(TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION)
      .onComplete(context.succeeding(job -> {
        assertThat(job.getError(), is(nullValue()));
        validateTlrMigrationResult(context);
      }));
  }


  @Test
  public void requestSearchMigrationShouldBeSkippedWhenUpgradingToLowerVersion(VertxTestContext context) {
    postTenant(REQ_SEARCH_MIGRATION_OLD_MOD_VER, REQ_SEARCH_MIGRATION_PREV_MOD_VER)
      .onComplete(context.succeeding(job -> {
        assertThatNoRequestsWereUpdatedByMigration(context, "searchIndex");
        context.completeNow();
      }));
  }

  @Test
  public void requestSearchMigrationShouldBeSkippedWhenUpgradingFromAlreadyMigratedVersion(
    VertxTestContext context) {

    postTenant(REQ_SEARCH_MIGRATION_MOD_VER, REQ_SEARCH_MIGRATION_NEXT_MOD_VER)
      .onComplete(context.succeeding(job -> {
        assertThatNoRequestsWereUpdatedByMigration(context, "searchIndex");
        context.completeNow();
      }));
  }

  @Test
  public void jobCompletedWhenRequestSearchMigrationIsSuccessful(VertxTestContext context) {
    postTenant(REQ_SEARCH_MIGRATION_PREV_MOD_VER, REQ_SEARCH_MIGRATION_MOD_VER)
      .compose(job -> {
        context.verify(() -> {
          assertThat("jobCompletedWhenRequestSearchMigrationIsSuccessful: job error present: " +
            job.getError(), job.getError(), is(nullValue()));
        });
        return getAllRequestsAsJson();
      })
      .onComplete(context.succeeding(requestsAfterMigration -> {
        context.verify(() -> {
          assertThat(requestsBeforeMigration.size(), is(requestsAfterMigration.size()));
          requestsAfterMigration.forEach(this::validateRequestSearchIndexFields);
        });
        context.completeNow();
      }));
  }

  @Test
  public void tlrMigrationFailsWhenItemStorageCallFails(VertxTestContext context) {
    wireMock.removeStub(itemStorageStub);
    tlrMigrationFailsWhenRemoteCallFails(context);
  }

  @Test
  public void tlrMigrationFailsWhenHoldingsStorageCallFails(VertxTestContext context) {
    wireMock.removeStub(holdingsStorageStub);
    tlrMigrationFailsWhenRemoteCallFails(context);
  }

  @Test
  public void tlrMigrationFailsWhenItemWasNotFound(VertxTestContext context) {
    wireMock.removeStub(itemStorageStub);
    tlrMigrationFailsWhenRemoteCallFails(context);
  }

  private void tlrMigrationFailsWhenRemoteCallFails(VertxTestContext context) {
    postTenant(TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION)
      .onComplete(context.succeeding(job -> {
        context.verify(() -> {
          assertThat(job.getError().contains("Request failed: GET"), is(true));
          assertThat(job.getError().contains("Response: [404]"), is(true));
        });
        assertThatNoRequestsWereUpdatedByMigration(context, "requestLevel");
        context.completeNow();
      }));
  }

  @Test
  public void requestSearchMigrationFailsWhenItemStorageCallFails(VertxTestContext context) {
    wireMock.removeStub(itemStorageStub);
    requestSearchMigrationFailsWhenRemoteCallFails(context);
  }

  @Test
  public void requestSearchMigrationFailsWhenServicePointsStorageCallFails(VertxTestContext context) {
    wireMock.removeStub(servicePointsStorageStub);
    requestSearchMigrationFailsWhenRemoteCallFails(context);
  }

  private void requestSearchMigrationFailsWhenRemoteCallFails(VertxTestContext context) {
    postTenant(REQ_SEARCH_MIGRATION_PREV_MOD_VER, REQ_SEARCH_MIGRATION_MOD_VER)
      .onComplete(context.succeeding(job -> {
        context.verify(() -> {
          assertThat(job.getError().contains("Request failed: GET"), is(true));
          assertThat(job.getError().contains("Response: [404]"), is(true));
        });
        assertThatNoRequestsWereUpdatedByMigration(context, "searchIndex");
        context.completeNow();
      }));
  }

  @Test
  public void useDefaultValuesForInstanceIdAndHoldingsRecordIdWhenItemWasNotFound(VertxTestContext context) {
    JsonObject randomRequest = requestsBeforeMigration.values()
      .stream()
      .findAny()
      .orElseThrow();

    randomRequest.put("itemId", UUID.randomUUID());
    String requestId = getId(randomRequest);

    postgresClient.update(REQUEST_TABLE_NAME, randomRequest, requestId)
      .compose(r -> postTenant(TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION))
      .compose(job -> getRequestAsJson(requestId))
      .onComplete(context.succeeding(updatedRequest -> {
        context.verify(() -> {
          assertThat(updatedRequest.getString("instanceId"), is(DEFAULT_UUID));
          assertThat(updatedRequest.getString("holdingsRecordId"), is(DEFAULT_UUID));
        });
        validateTlrMigrationResult(context);
      }));

  }

  @Test
  public void changesMadeForAllBatchesAreRevertedInCaseOfError(VertxTestContext context) {
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

    postTenant(TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION)
      .onComplete(context.succeeding(job -> {
        context.verify(() -> assertThat(job.getError().isEmpty(), is(false)));
        assertThatNoRequestsWereUpdatedByMigration(context, "requestLevel");
        context.completeNow();
      }));
  }

  @Test
  public void jobFailsWhenRequestAlreadyHasTitleLevelRequestField(VertxTestContext context) {
    JsonObject randomRequest = requestsBeforeMigration.values()
      .stream()
      .findAny()
      .orElseThrow();

    randomRequest.put("requestLevel", "item");

    jobFailsWhenRequestValidationFails(context, randomRequest,
      "request already contains TLR fields: " + getId(randomRequest));
  }

  @Test
  public void jobFailsWhenRequestDoesNotHaveRequiredItemLevelRequestField(VertxTestContext context) {
    JsonObject randomRequest = requestsBeforeMigration.values()
      .stream()
      .findAny()
      .orElseThrow();

    randomRequest.remove("itemId");

    jobFailsWhenRequestValidationFails(context, randomRequest,
      "request does not contain required ILR fields: " + getId(randomRequest));
  }

  @Test
  public void migrationRemovesPositionFromClosedRequests(VertxTestContext context) {
    List<String> closedStatuses = Stream.of(CLOSED_FILLED, CLOSED_UNFILLED, CLOSED_PICKUP_EXPIRED,
        CLOSED_CANCELLED)
      .map(Request.Status::value)
      .collect(toList());

    postTenant(TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION)
      .compose(job -> getAllRequestsAsJson())
      .onComplete(context.succeeding(requestsAfterMigration -> {
        context.verify(() -> {
          for (JsonObject request : requestsAfterMigration) {
            Integer position = request.getInteger("position");
            if (closedStatuses.contains(request.getString("status"))) {
              assertThat(position, is(nullValue()));
            } else {
              assertThat(position, is(notNullValue()));
            }
          }
        });
        context.completeNow();
      }));
  }

  @Test
  public void migrationShouldCorrectFulfillmentPreferenceSpelling(VertxTestContext context) {
    postTenant(REQ_FULFILLMENT_PREFERENCE_SPELLING_PREV_VER,
      REQ_FULFILLMENT_PREFERENCE_SPELLING_VER)
      .compose(job -> getAllRequestsAsJson())
      .onComplete(context.succeeding(requestsAfterMigration -> {
        context.verify(() -> {
          requestsAfterMigration.forEach(request -> {
            // Ensure using nullValue() matcher to avoid NPE in Hamcrest
            assertThat(request.getString("fulfilmentPreference"), is(nullValue()));
            assertThat(request.getString("fulfillmentPreference"), is(notNullValue()));
          });
        });
        context.completeNow();
      }));
  }

  @Test
  public void migrationShouldNotCorrectFulfillmentPreferenceSpellingFromAlreadyMigratedVersion(
    VertxTestContext context) {

    postTenant(REQ_FULFILLMENT_PREFERENCE_SPELLING_VER,
      REQ_FULFILLMENT_PREFERENCE_SPELLING_NEXT_VER)
      .onComplete(context.succeeding(job -> {
        assertThatNoRequestsWereUpdatedByMigration(context, "fulfillmentPreference");
        context.completeNow();
      }));
  }

  @Test
  public void migrationShouldNotCorrectFulfillmentPreferenceSpellingFromMigratedVersionToOlder(
    VertxTestContext context) {

    postTenant(REQ_FULFILLMENT_PREFERENCE_SPELLING_VER,
      REQ_FULFILLMENT_PREFERENCE_SPELLING_PREV_VER)
      .onComplete(context.succeeding(job -> {
        assertThatNoRequestsWereUpdatedByMigration(context, "fulfillmentPreference");
        context.completeNow();
      }));
  }

  @Test
  public void keepReferenceData(VertxTestContext context) {
    setOtherCancellationReasonName("foo")
      .compose(x -> assertOtherCancellationReasonName("foo"))
      .compose(x -> postTenant("16.1.0", ModuleName.getModuleVersion()))
      .compose(x -> assertOtherCancellationReasonName("foo"))
      .compose(x -> postTenant("0.0.0", ModuleName.getModuleVersion()))
      .compose(x -> assertOtherCancellationReasonName("Other"))
      .onComplete(context.succeeding(v -> context.completeNow()));
  }

  private void jobFailsWhenRequestValidationFails(VertxTestContext context,
    JsonObject request, String expectedErrorMessage) {

    postgresClient.update(REQUEST_TABLE_NAME, request, getId(request))
      .compose(r -> postTenant(TLR_MIGRATION_PREV_MODULE_VERSION, TLR_MIGRATION_MODULE_VERSION))
      .onComplete(context.succeeding(job -> {
        context.verify(() -> {
          assertThat(job.getError().contains(expectedErrorMessage), is(true));
        });
        assertThatNoRequestsWereUpdatedByMigration(context, "requestLevel");
        context.completeNow();
      }));
  }

  private static Future<TenantJob> postTenant(String fromVersion, String toVersion) {
    return tenantClient.postTenant(getTenantAttributes(fromVersion, toVersion))
      .map(response -> response.bodyAsJson(TenantJob.class))
      .compose(job -> tenantClient.getTenantByOperationId(job.getId(), GET_TENANT_TIMEOUT_MS))
      .map(response -> {
        TenantJob job = response.bodyAsJson(TenantJob.class);
        jobId = job.getId();
        return job;
      });
  }

  private static void assertThatNoRequestsWereUpdatedByMigration(VertxTestContext context,
    String field) {

    selectRead(format("SELECT COUNT(*) " +
      "FROM " + REQUEST_TABLE + " " +
      "WHERE jsonb->>'%s' IS NOT null", field))
      .onComplete(context.succeeding(rowSet -> {
        context.verify(() -> assertThat(getCount(rowSet), is(0)));
        context.completeNow();
      }));
  }

  private static Future<List<JsonObject>> getAllRequestsAsJson() {
    return selectRead("SELECT * FROM " + REQUEST_TABLE)
      .map(rowSet -> StreamSupport.stream(rowSet.spliterator(), false)
        .map(row -> row.getJsonObject("jsonb"))
        .collect(toList()));
  }

  private static Future<RowSet<Row>> selectRead(String sql) {
    Promise<RowSet<Row>> promise = Promise.promise();
    postgresClient.selectRead(sql, 0, promise::handle);
    return promise.future();
  }

  private static Future<JsonObject> getRequestAsJson(String requestId) {
    return postgresClient.getById(REQUEST_TABLE_NAME, requestId);
  }

  private static Future<RowSet<Row>> setOtherCancellationReasonName(String name) {
    var json = new JsonObject()
      .put("id", OTHER_CANCELLATION_REASON_ID)
      .put("name", name)
      .put("description", "Other")
      .put("requiresAdditionalInformation", true);
    return postgresClient.execute("UPDATE cancellation_reason SET jsonb=$1 WHERE id=$2",
      Tuple.of(json, OTHER_CANCELLATION_REASON_ID));
  }

  private static Future<Row> assertOtherCancellationReasonName(String expected) {
    return postgresClient.selectSingle("SELECT jsonb->>'name' FROM cancellation_reason WHERE id=$1",
        Tuple.of(OTHER_CANCELLATION_REASON_ID))
      .map(row -> {
        assertThat(row.getString(0), is(expected));
        return row;
      });
  }

  protected static Future<RowSet<Row>> loadRequests() throws Exception {
    InputStream tableInput = TenantRefApiTests.class.getClassLoader().getResourceAsStream(
      "mocks/TlrDataMigrationTestData.sql");
    String sqlFile = IOUtils.toString(Objects.requireNonNull(tableInput), StandardCharsets.UTF_8);
    return postgresClient.execute(sqlFile);
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

  private void validateTlrMigrationResult(VertxTestContext context) {
    getAllRequestsAsJson()
      .onFailure(context::failNow)
      .onSuccess(requestsAfterMigration -> {
        context.verify(() -> {
          assertThat(requestsAfterMigration.size(), is(requestsBeforeMigration.size()));
          requestsAfterMigration.forEach(request -> validateTlrMigrationResult(request));
        });
        context.completeNow();
      });
  }

  private void validateTlrMigrationResult(JsonObject requestAfter) {
    JsonObject requestBefore = requestsBeforeMigration.get(getId(requestAfter));

    assertThat(requestBefore, is(org.hamcrest.Matchers.notNullValue()));
    assertThat(requestAfter, is(org.hamcrest.Matchers.notNullValue()));

    assertThat(requestAfter.getString("requestLevel"), is("Item"));
    assertThat(requestAfter.getString("instanceId"), is(org.hamcrest.Matchers.notNullValue()));
    assertThat(requestAfter.getString("holdingsRecordId"), is(org.hamcrest.Matchers.notNullValue()));

    if (requestBefore.containsKey("item")) {
      JsonObject itemBefore = requestBefore.getJsonObject("item");
      JsonObject itemAfter = requestAfter.getJsonObject("item");
      JsonObject instance = requestAfter.getJsonObject("instance");

      if (itemBefore.containsKey("title")) {
        assertThat(instance.getString("title"), is(itemBefore.getString("title")));
      }

      if (itemBefore.containsKey("identifiers")) {
        assertThat(instance.getJsonArray("identifiers"), is(itemBefore.getJsonArray("identifiers")));
      }

      assertThat(itemAfter.containsKey("title"), is(false));
      assertThat(itemAfter.containsKey("identifiers"), is(false));
    }
  }

  private void validateRequestSearchMigrationResult(VertxTestContext context) {
    getAllRequestsAsJson()
      .onFailure(context::failNow)
      .onSuccess(requestsAfterMigration -> {
        assertThat(requestsBeforeMigration.size(), is(requestsAfterMigration.size()));
        requestsAfterMigration.forEach(this::validateTlrMigrationResult);
        context.completeNow();
      });
  }

  private void validateRequestSearchIndexFields(JsonObject requestAfter) {
    JsonObject searchIndex = requestAfter.getJsonObject("searchIndex");

    if (searchIndex != null) {
      String requestId = getId(requestAfter);

      // Validate that search index fields are populated
      if (!REQUEST_ID_MISSING_EFFECTIVE_SHELVING_ORDER.equals(requestId)) {
        assertThat(searchIndex.getString("shelvingOrder"), is(notNullValue()));
      }

      if (!REQUEST_ID_MISSING_CALL_NUMBER.equals(requestId) &&
          !REQUEST_ID_MISSING_EFFECTIVE_CALL_NUMBER_COMPONENTS.equals(requestId)) {
        assertThat(searchIndex.getString("callNumberComponents"), is(notNullValue()));
      }

      if (!REQUEST_ID_MISSING_PICKUP_SERVICE_POINT_NAME.equals(requestId)) {
        String pickupServicePointName = searchIndex.getString("pickupServicePointName");
        if (requestAfter.getString("pickupServicePointId") != null) {
          assertThat(pickupServicePointName, is(notNullValue()));
        }
      }
    }
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

