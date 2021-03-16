package org.folio.rest.api.migration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class StaffSlipsHoldTransitMigrationScriptTest extends MigrationTestBase {
  public static final String HOLD_ID = "6a6e72f0-69da-4b4c-8254-7154679e9d88";
  public static final String TRANSIT_ID = "f838cdaf-555a-473f-abf1-f35ef6ab8ae1";
  public static final String TEMPLATE = "<p></p>";
  private static final String MIGRATION_SCRIPT = loadScript("add_staff_slips_hold_transit.sql");

  @Before
  public void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(staffSlipsStorageUrl(""));
  }

  @Test
  public void canMigrateStaffSlips() throws Exception {

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

  private JsonObject getRecordById(JsonArray collection, String id) {
    return collection.stream()
      .map(index -> (JsonObject) index)
      .filter(request -> StringUtils.equals(request.getString("id"), id))
      .findFirst().orElse(null);
  }

  private void assertStaffSlip(JsonObject staffSlip, String expectedId,
    String expectedName) {

    assertThat(staffSlip.getString("id"), is(expectedId));
    assertThat(staffSlip.getString("name"), is(expectedName));
    assertThat(staffSlip.getBoolean("active"), is(true));
    assertThat(staffSlip.getString("template"), is(TEMPLATE));
  }

  private URL staffSlipsStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/staff-slips-storage/staff-slips" + subPath);
  }
}
