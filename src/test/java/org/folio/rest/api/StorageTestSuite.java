package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.RestVerticle;
import org.folio.rest.api.loans.LoansAnonymizationApiTest;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  LoansApiTest.class,
  LoansAnonymizationApiTest.class,
  LoanRulesApiTest.class,
  FixedDueDateApiTest.class,
  LoanPoliciesApiTest.class,
  RequestsApiTest.class,
  LoansApiHistoryTest.class,
  StaffSlipsApiTest.class,
  CancellationReasonsApiTest.class,
  PatronNoticePoliciesApiTest.class
})

public class StorageTestSuite {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String TENANT_ID = "test_tenant";

	private static Vertx vertx;
	private static int port;
	private static boolean initialised = false;

	public static URL storageUrl(String path) throws MalformedURLException {
		return new URL("http", "localhost", port, path);
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

		port = NetworkUtils.nextFreePort();

		DeploymentOptions options = new DeploymentOptions();

		options.setConfig(new JsonObject().put("http.port", port));
		options.setWorker(true);

		startVerticle(options);

		prepareTenant(TENANT_ID);

		initialised = true;
	}

	@AfterClass
	public static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

		initialised = false;

		removeTenant(TENANT_ID);

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
		HttpClient client = new HttpClient(getVertx());

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

	public static void checkForMismatchedIDs(String table) {
		try {
			ResultSet results = getRecordsWithUnmatchedIds(TENANT_ID, table);

			Integer mismatchedRowCount = results.getNumRows();

			assertThat(mismatchedRowCount, is(0));

		} catch (Exception e) {
      log.error(String.format(
        "Unable to determine mismatched ID rows for '%s': '%s'",
        table, e.getMessage()), e);
      assert false;
		}
	}

	private static ResultSet getRecordsWithUnmatchedIds(
	  String tenantId,
    String tableName)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

		PostgresClient postgresClient = PostgresClient.getInstance(getVertx(), tenantId);

		CompletableFuture<ResultSet> selectCompleted = new CompletableFuture<>();

		String sql = String.format(
		  "SELECT null FROM %s_%s.%s" + " WHERE CAST(_id AS VARCHAR(50)) != jsonb->>'id'",
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

	private static void prepareTenant(String tenantId) {
		CompletableFuture<TextResponse> tenantPrepared = new CompletableFuture<>();

		log.info("Making request to prepare tenant in module");

		try {
			HttpClient client = new HttpClient(vertx);

			client.post(storageUrl("/_/tenant"), null, tenantId,
        ResponseHandler.text(tenantPrepared));

			TextResponse response = tenantPrepared.get(20, TimeUnit.SECONDS);

			String failureMessage = String.format("Tenant preparation failed: %s: %s",
        response.getStatusCode(), response.getBody());

			assertThat(failureMessage, response.getStatusCode(), is(201));

		} catch (Exception e) {
			log.error("Tenant preparation failed: " + e.getMessage(), e);
			assert false;
		}
	}

	private static void removeTenant(String tenantId) {
		CompletableFuture<TextResponse> tenantDeleted = new CompletableFuture<>();

    log.info("Making request to clean up tenant in module");

		try {
			HttpClient client = new HttpClient(vertx);

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
	}
}
