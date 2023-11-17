package org.folio.rest.api.migration;

import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SearchSlipsMigrationScriptTest extends StaffSlipsMigrationTestBase {
  public static final String SEARCH_SLIP_ID = "e6e29ec1-1a76-4913-bbd3-65f4ffd94e03";
  private static final String MIGRATION_SCRIPT = loadScript("add_search_slips.sql");

  @Before
  public void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(staffSlipsStorageUrl(""));
  }

  @Test
  public void canMigrateStaffSlips() throws Exception {
    executeMultipleSqlStatements(MIGRATION_SCRIPT);
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(staffSlipsStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));
    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), Is.is(HttpURLConnection.HTTP_OK));

    JsonArray slipsJsonArray = getResponse.getJson().getJsonArray("staffSlips");
    JsonObject hold = getRecordById(slipsJsonArray, SEARCH_SLIP_ID);

    assertStaffSlip(hold, SEARCH_SLIP_ID, "Search slip (Hold requests)");
  }
}
