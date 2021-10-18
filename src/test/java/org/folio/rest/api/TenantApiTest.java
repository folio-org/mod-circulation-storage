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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
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

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

@RunWith(VertxUnitRunner.class)
public class TenantApiTest {
  protected static final Logger log = LogManager.getLogger(TenantApiTest.class);

  protected static final String OLD_MODULE_VERSION = "13.0.0";
  protected static final String PREVIOUS_MODULE_VERSION = "13.1.0";
  protected static final String MIGRATION_MODULE_VERSION = "13.2.0";
  protected static final String NEXT_MODULE_VERSION = "13.4.0";
  protected static final String MODULE_NAME = "mod_patron_blocks";
  protected static final int OKAPI_PORT = NetworkUtils.nextFreePort();
  protected static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;
  protected static final String OKAPI_TENANT = "test_tenant";
  protected static final String OKAPI_TOKEN = generateOkapiToken();
  private static final int GET_TENANT_TIMEOUT_MS = 10000;
  protected static final String REQUEST_TABLE = "request";

  protected static Vertx vertx;
  protected static TenantClient tenantClient;
  protected static PostgresClient postgresClient;
  protected static String jobId;

  @ClassRule
  public static WireMockRule wireMock = new WireMockRule(
    new WireMockConfiguration().dynamicPort());

  @BeforeClass
  public static void beforeAll(final TestContext context) throws Exception {
    Async async = context.async();

    vertx = Vertx.vertx();
    postgresClient = PostgresClient.getInstance(vertx, OKAPI_TENANT);
    tenantClient = new TenantClient(getMockedOkapiUrl(), OKAPI_TENANT, OKAPI_TOKEN,
      WebClient.create(vertx));

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", OKAPI_PORT));

    vertx.deployVerticle(RestVerticle.class.getName(), deploymentOptions,
      deployment -> async.complete());
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

  @Before
  public void beforeEach() throws Exception {
    PostgresClient.closeAllClients();
    postgresClient = PostgresClient.getInstance(vertx, OKAPI_TENANT);
    reLoadTestData();
    mockEndpoints();
  }

  @Test
  public void migrationShouldBeSkippedWhenUpgradingToLowerVersion(final TestContext context) {
    Async async = context.async();

    try {
      tenantClient.postTenant(getTenantAttributes(OLD_MODULE_VERSION,
        PREVIOUS_MODULE_VERSION), postResult -> {
        if (postResult.failed()) {
          log.error(postResult.cause());
          return;
        }

        final HttpResponse<Buffer> postResponse = postResult.result();
        assertThat(postResponse.statusCode(), is(HttpStatus.HTTP_CREATED.toInt()));

        jobId = postResponse.bodyAsJson(TenantJob.class).getId();

        tenantClient.getTenantByOperationId(jobId, GET_TENANT_TIMEOUT_MS, getResult -> {
          final HttpResponse<Buffer> getResponse = getResult.result();

          context.assertEquals(getResponse.statusCode(), HttpStatus.HTTP_OK.toInt());
          context.assertEquals(getResponse.bodyAsJson(TenantJob.class).getComplete(), true);

          postgresClient.select("SELECT COUNT(*) " +
            "FROM test_tenant_mod_circulation_storage.request " +
            "WHERE jsonb->>'requestLevel' IS NOT null")
            .onComplete(ar -> {
              if (ar.succeeded()) {
                Integer count = ar.result().iterator().next().get(Integer.class, 0);
                // TODO:: check the number of updated requests
              }
              else {
                context.fail("Can not count requests");
              }

              async.complete();
            });
        });
      });
    } catch (Exception e) {
      context.fail(e);
    }
  }

  @Test
  public void jobCompleteWhenMigrationIsSuccessful(final TestContext context) {
    Async async = context.async();

    try {
      tenantClient.postTenant(getTenantAttributes(PREVIOUS_MODULE_VERSION,
        MIGRATION_MODULE_VERSION), postResult -> {
        if (postResult.failed()) {
          log.error(postResult.cause());
          return;
        }

        final HttpResponse<Buffer> postResponse = postResult.result();
        assertThat(postResponse.statusCode(), is(HttpStatus.HTTP_CREATED.toInt()));

        jobId = postResponse.bodyAsJson(TenantJob.class).getId();

        tenantClient.getTenantByOperationId(jobId, GET_TENANT_TIMEOUT_MS, getResult -> {
          final HttpResponse<Buffer> getResponse = getResult.result();

          context.assertEquals(getResponse.statusCode(), HttpStatus.HTTP_OK.toInt());
          context.assertEquals(getResponse.bodyAsJson(TenantJob.class).getComplete(), true);
          context.assertNull(getResponse.bodyAsJson(TenantJob.class).getError());

          async.complete();
        });
      });
    } catch (Exception e) {
      context.fail(e);
    }
  }

  @Test
  public void jobFailsWhenItemNotFound(final TestContext context) {
    wireMock.removeStub(get(urlMatching("/item-storage/items/.*")));

    Async async = context.async();

    try {
      tenantClient.postTenant(getTenantAttributes(PREVIOUS_MODULE_VERSION,
        MIGRATION_MODULE_VERSION), postResult -> {

        if (postResult.failed()) {
          log.error(postResult.cause());
          return;
        }

        final HttpResponse<Buffer> postResponse = postResult.result();
        assertThat(postResponse.statusCode(), is(HttpStatus.HTTP_CREATED.toInt()));

        jobId = postResponse.bodyAsJson(TenantJob.class).getId();

        tenantClient.getTenantByOperationId(jobId, GET_TENANT_TIMEOUT_MS, getResult -> {
          final HttpResponse<Buffer> getResponse = getResult.result();

          context.assertEquals(getResponse.statusCode(), HttpStatus.HTTP_OK.toInt());
          context.assertEquals(getResponse.bodyAsJson(TenantJob.class).getComplete(), true);
          context.assertEquals(getResponse.bodyAsString().contains(
            "failed to determine instanceId, request "), true);

          async.complete();
        });
      });
    } catch (Exception e) {
      context.fail(e);
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
    wireMock.resetAll();

    wireMock.stubFor(post(urlEqualTo("/pubsub/event-types"))
      .atPriority(100)
      .willReturn(created()));

    wireMock.stubFor(post(urlEqualTo("/pubsub/event-types?"))
      .atPriority(100)
      .willReturn(created()));

    wireMock.stubFor(post(urlMatching("/pubsub/event-types/declare/(publisher|subscriber)"))
      .atPriority(100)
      .willReturn(created()));

    wireMock.stubFor(get(urlMatching("/item-storage/items/.*"))
      .atPriority(100)
      .willReturn(ok().withBody(buildItemJson())));

    wireMock.stubFor(get(urlMatching("/holdings-storage/holdings/.*"))
      .atPriority(100)
      .willReturn(ok().withBody(buildHoldingsRecordJson())));

    // forward everything else to Okapi
    wireMock.stubFor(any(anyUrl())
      .atPriority(Integer.MAX_VALUE)
      .willReturn(aResponse().proxiedFrom(OKAPI_URL)));
  }

  private static String buildItemJson() {
    return new JsonObject()
      .put("holdingsRecordId", UUID.randomUUID().toString())
      .encode();
  }

  private static String buildHoldingsRecordJson() {
    return new JsonObject()
      .put("instanceId", UUID.randomUUID().toString())
      .encode();
  }

  protected static TenantAttributes getTenantAttributes(String moduleFrom, String moduleTo) {
    final Parameter loadReferenceParameter = new Parameter()
      .withKey("loadReference").withValue("true");

    return new TenantAttributes()
      .withModuleFrom(format("%s-%s", MODULE_NAME, moduleFrom))
      .withModuleTo(format("%s-%s", MODULE_NAME, moduleTo))
      .withParameters(Collections.singletonList(loadReferenceParameter));
  }

  protected void deleteTestData() {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    postgresClient.delete(REQUEST_TABLE, new Criterion(), result -> future.complete(null));
    try {
      future.get(5, TimeUnit.SECONDS);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  protected void reLoadTestData() throws Exception {
    deleteTestData();

    InputStream tableInput = TenantApiTest.class.getClassLoader().getResourceAsStream(
      "mocks/TlrDataMigrationTestData.sql");
    String sqlFile = IOUtils.toString(Objects.requireNonNull(tableInput), StandardCharsets.UTF_8);
    CompletableFuture<Void> dataLoaded = new CompletableFuture<>();
    postgresClient.runSQLFile(sqlFile, true)
      .onComplete(ar -> dataLoaded.complete(null));
    dataLoaded.get(5, TimeUnit.SECONDS);
  }

  private static String generateOkapiToken() {
    final String payload = new JsonObject()
      .put("user_id", randomId())
      .put("tenant", OKAPI_TENANT)
      .put("sub", "admin")
      .toString();

    return format("1.%s.3", Base64.getEncoder()
      .encodeToString(payload.getBytes()));
  }

  protected static String getMockedOkapiUrl() {
    return "http://localhost:" + wireMock.port();
  }

  protected static String randomId() {
    return UUID.randomUUID().toString();
  }
}
