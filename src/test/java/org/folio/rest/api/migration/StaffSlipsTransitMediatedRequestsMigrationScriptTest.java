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

public class StaffSlipsTransitMediatedRequestsMigrationScriptTest extends StaffSlipsMigrationTestBase{

  private static final String STAFF_SLIP_ID = "e6e29ec1-1a76-4913-bbd3-65f4ffd94e04";
  private static final String SCRIPT_NAME = "add_staff_slips_transit_mediated_requests.sql";
  private static final String MIGRATION_SCRIPT = loadScript(SCRIPT_NAME);
  private static final String STAFF_SLIPS_KEY = "staffSlips";
  private static final String STAFF_SLIPS_SUB_PATH = "";
  private static final String STAFF_SLIP_NAME = "Transit (mediated requests)";
  private static final int TIMEOUT_VALUE = 5;

  @Before
  public void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(staffSlipsStorageUrl(STAFF_SLIPS_SUB_PATH));
  }

  @Test
  public void canMigrateStaffSlips() throws Exception {
    executeMultipleSqlStatements(MIGRATION_SCRIPT);
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(staffSlipsStorageUrl(STAFF_SLIPS_SUB_PATH), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));
    JsonResponse getResponse = getCompleted.get(TIMEOUT_VALUE, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), Is.is(HttpURLConnection.HTTP_OK));

    JsonArray slipsJsonArray = getResponse.getJson().getJsonArray(STAFF_SLIPS_KEY);
    JsonObject staffSlips = getRecordById(slipsJsonArray, STAFF_SLIP_ID);

    assertStaffSlip(staffSlips, STAFF_SLIP_ID, STAFF_SLIP_NAME);
  }
}
