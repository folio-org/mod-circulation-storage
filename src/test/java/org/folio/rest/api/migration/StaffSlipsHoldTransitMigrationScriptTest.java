package org.folio.rest.api.migration;

import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class StaffSlipsHoldTransitMigrationScriptTest extends StaffSlipsMigrationTestBase {
  public static final String HOLD_ID = "6a6e72f0-69da-4b4c-8254-7154679e9d88";
  public static final String TRANSIT_ID = "f838cdaf-555a-473f-abf1-f35ef6ab8ae1";
  private static final String MIGRATION_SCRIPT = loadScript("add_staff_slips_hold_transit.sql");

  @BeforeEach
  void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(staffSlipsStorageUrl(""));
  }

  @Test
  void canMigrateStaffSlips() throws Exception {

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(staffSlipsStorageUrl(""), StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), Is.is(HttpURLConnection.HTTP_OK));

    JsonArray slipsJsonArray = getResponse.getJson().getJsonArray("staffSlips");

    JsonObject hold = getRecordById(slipsJsonArray, HOLD_ID);
    JsonObject transit = getRecordById(slipsJsonArray, TRANSIT_ID);

    assertStaffSlip(hold, HOLD_ID, "Hold");
    assertStaffSlip(transit, TRANSIT_ID, "Transit");
  }
}
