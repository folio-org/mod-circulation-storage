package org.folio.rest.api.migration;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertThat;

public class DueDateSlipsMigrationScriptTest extends StaffSlipsMigrationTestBase {
  public static final String DUE_DATE_RECEIPT_ID = "0b52bca7-db17-4e91-a740-7872ed6d7323";
  private static final String MIGRATION_SCRIPT = loadScript("add_due_date_slips.sql");

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
    JsonObject staffSlips = getRecordById(slipsJsonArray, DUE_DATE_RECEIPT_ID);

    assertStaffSlip(staffSlips, DUE_DATE_RECEIPT_ID, "Due date receipt");
  }
}
