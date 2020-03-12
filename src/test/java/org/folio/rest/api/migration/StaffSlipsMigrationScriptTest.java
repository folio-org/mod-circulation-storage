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

public class StaffSlipsMigrationScriptTest extends MigrationTestBase {
  public static final String PICK_SLIPS_ID = "8812bae1-2738-442c-bc20-fe4bb38a11f8";
  public static final String REQUEST_DELIVERY_ID = "1ed55c5c-64d9-40eb-8b80-7438a262288b";
  public static final String TEMPLATE = "<p></p>";
  private static final String MIGRATION_SCRIPT = loadScript("add_staff_slips.sql");

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

    JsonObject pickSlips = getRecordById(slipsJsonArray, PICK_SLIPS_ID);
    JsonObject requestDelivery = getRecordById(slipsJsonArray, REQUEST_DELIVERY_ID);

    assertStaffSlip(pickSlips, PICK_SLIPS_ID, "Pick slip");
    assertStaffSlip(requestDelivery, REQUEST_DELIVERY_ID, "Request delivery");
  }

  private JsonObject getRecordById(JsonArray collection, String id) {
    return collection.stream()
      .map(index -> (JsonObject) index)
      .filter(request -> StringUtils.equals(request.getString("id"), id))
      .findFirst().get();
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
