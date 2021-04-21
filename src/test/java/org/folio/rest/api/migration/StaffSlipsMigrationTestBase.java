package org.folio.rest.api.migration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.api.StorageTestSuite;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class StaffSlipsMigrationTestBase extends MigrationTestBase {
  public static final String TEMPLATE = "<p></p>";

  JsonObject getRecordById(JsonArray collection, String id) {
    return collection.stream()
      .map(index -> (JsonObject) index)
      .filter(request -> StringUtils.equals(request.getString("id"), id))
      .findFirst().orElse(null);
  }

  void assertStaffSlip(JsonObject staffSlip, String expectedId,
    String expectedName) {

    assertThat(staffSlip.getString("id"), is(expectedId));
    assertThat(staffSlip.getString("name"), is(expectedName));
    assertThat(staffSlip.getBoolean("active"), is(true));
    assertThat(staffSlip.getString("template"), is(TEMPLATE));
  }

  URL staffSlipsStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/staff-slips-storage/staff-slips" + subPath);
  }
}
