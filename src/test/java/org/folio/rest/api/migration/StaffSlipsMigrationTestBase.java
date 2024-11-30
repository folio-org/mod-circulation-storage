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
  private static final String TEMPLATE = "<p></p>";
  private static final String ID_KEY = "id";
  private static final String NAME_KEY = "name";
  private static final String ACTIVE_KEY = "active";
  private static final String TEMPLATE_KEY = "template";
  private static final String STORAGE_URL = "/staff-slips-storage/staff-slips";

  JsonObject getRecordById(JsonArray collection, String id) {
    return collection.stream()
      .map(index -> (JsonObject) index)
      .filter(request -> StringUtils.equals(request.getString(ID_KEY), id))
      .findFirst().orElse(null);
  }

  void assertStaffSlip(JsonObject staffSlip, String expectedId,
    String expectedName) {

    assertThat(staffSlip.getString(ID_KEY), is(expectedId));
    assertThat(staffSlip.getString(NAME_KEY), is(expectedName));
    assertThat(staffSlip.getBoolean(ACTIVE_KEY), is(true));
    assertThat(staffSlip.getString(TEMPLATE_KEY), is(TEMPLATE));
  }

  URL staffSlipsStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl(STORAGE_URL + subPath);
  }
}
