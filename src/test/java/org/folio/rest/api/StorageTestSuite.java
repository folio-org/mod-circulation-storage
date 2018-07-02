package org.folio.rest.api;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import org.folio.rest.RestVerticle;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  LoansApiTest.class,
  LoanRulesApiTest.class,
  FixedDueDateApiTest.class,
  LoanPoliciesApiTest.class,
  RequestsApiTest.class,
  LoansApiHistoryTest.class,
  StaffSlipsApiTest.class,
  CancellationReasonsApiTest.class
})

public class StorageTestSuite {
	static final String TENANT_ID = "test_tenant";
  private static final int TENANT_API_TIMEOUT = 20;
  private static final int VERTICLE_OPERATION_TIMEOUT = 20;

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
	public static void before() throws Exception {

		vertx = Vertx.vertx();

		String useExternalDatabase = System.getProperty("org.folio.circulation.storage.test.database", "embedded");

		switch (useExternalDatabase) {
		case "environment":
			System.out.println("Using environment settings");
			break;

		case "external":
			String postgresConfigPath = System.getProperty("org.folio.circulation.storage.test.config",
					"/postgres-conf-local.json");

			PostgresClient.setConfigFilePath(postgresConfigPath);
			break;
		case "embedded":
			PostgresClient.setIsEmbedded(true);
			PostgresClient.setEmbeddedPort(NetworkUtils.nextFreePort());
			PostgresClient client = PostgresClient.getInstance(vertx);
			client.startEmbeddedPostgres();
			break;
		default:
			String message = "No understood database choice made." + "Please set org.folio.circulation.storage.test.config"
					+ "to 'external', 'environment' or 'embedded'";

			throw new Exception(message);
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
	public static void after() throws InterruptedException, ExecutionException, TimeoutException {

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

		undeploymentComplete.get(VERTICLE_OPERATION_TIMEOUT, TimeUnit.SECONDS);
	}

	public static boolean isNotInitialised() {
		return !initialised;
	}

	static void deleteAll(URL rootUrl) {
		HttpClient client = new HttpClient(getVertx());

		CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

		try {
			client.delete(rootUrl, TENANT_ID, ResponseHandler.empty(deleteAllFinished));

			Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

			if (response.getStatusCode() != HTTP_NO_CONTENT) {
				System.out.println("WARNING!!!!! Delete all resources preparation failed");
			}
		} catch (Exception e) {
			System.out.println("WARNING!!!!! Unable to delete all resources: " + e.getMessage());
		}
	}

	static void checkForMismatchedIDs(String table) {
		try {
			ResultSet results = getRecordsWithUnmatchedIds(TENANT_ID, table);

			Integer mismatchedRowCount = results.getNumRows();

			assertThat(mismatchedRowCount, is(0));
		} catch (Exception e) {
			System.out.println(String.format("WARNING!!!!! Unable to determine mismatched ID rows for %s", table));
		}
	}

	private static ResultSet getRecordsWithUnmatchedIds(String tenantId, String tableName)
			throws InterruptedException, ExecutionException, TimeoutException {

		PostgresClient dbClient = PostgresClient.getInstance(getVertx(), tenantId);

		CompletableFuture<ResultSet> selectCompleted = new CompletableFuture<>();

		String sql = String.format("SELECT null FROM %s_%s.%s" + " WHERE CAST(_id AS VARCHAR(50)) != jsonb->>'id'",
				tenantId, "mod_circulation_storage", tableName);

		dbClient.select(sql, result -> {
			if (result.succeeded()) {
				selectCompleted.complete(result.result());
			} else {
				selectCompleted.completeExceptionally(result.cause());
			}
		});

		return selectCompleted.get(5, TimeUnit.SECONDS);
	}

	private static void startVerticle(DeploymentOptions options)
			throws InterruptedException, ExecutionException, TimeoutException {

		CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

		vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
			if (res.succeeded()) {
				deploymentComplete.complete(res.result());
			} else {
				deploymentComplete.completeExceptionally(res.cause());
			}
		});

		deploymentComplete.get(VERTICLE_OPERATION_TIMEOUT, TimeUnit.SECONDS);
	}

	private static void prepareTenant(String tenantId) {
		CompletableFuture<TextResponse> tenantPrepared = new CompletableFuture<>();

		try {
			HttpClient client = new HttpClient(vertx);

			client.post(storageUrl("/_/tenant"), null, tenantId, ResponseHandler.text(tenantPrepared));
			
      TextResponse response = tenantPrepared.get(TENANT_API_TIMEOUT, TimeUnit.SECONDS);

			String failureMessage = String.format("Tenant preparation failed: %s: %s", response.getStatusCode(),
					response.getBody());

			assertThat(failureMessage, response.getStatusCode(), is(HTTP_CREATED));

		} catch (Exception e) {
			System.out.println("WARNING!!!!! Tenant preparation failed: " + e.getMessage());
			assert false;
		}
	}

	private static void removeTenant(String tenantId) {
		CompletableFuture<TextResponse> tenantDeleted = new CompletableFuture<>();

		try {
			HttpClient client = new HttpClient(vertx);

			client.delete(storageUrl("/_/tenant"), tenantId, ResponseHandler.text(tenantDeleted));

      TextResponse response = tenantDeleted.get(TENANT_API_TIMEOUT, TimeUnit.SECONDS);

			String failureMessage = String.format("Tenant cleanup failed: %s: %s", response.getStatusCode(),
					response.getBody());

			assertThat(failureMessage, response.getStatusCode(), is(HTTP_NO_CONTENT));

		} catch (Exception e) {
			System.out.println("WARNING!!!!! Tenant cleanup failed: " + e.getMessage());
			assert false;
		}
	}
}
