package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CirculationSettingsAPITest extends ApiTests {
  private static final String ID_KEY = "id";
  private static final String NAME_KEY = "name";
  private static final String VALUE_KEY = "value";
  private static final String SAMPLE_VALUE = "sample";
  private static final String SAMPLE_KEY = "sample";
  private static final String INITIAL_VALUE = "OK";
  private static final String UPDATED_VALUE = "OK1";
  private static final String TABLE_NAME = "circulation_settings";
  private static final String CIRCULATION_SETTINGS_PROPERTY = "circulation-settings";
  private static final int NOT_FOUND_STATUS = 404;

  private final AssertingRecordClient circulationSettingsClient =
    new AssertingRecordClient(
      client, StorageTestSuite.TENANT_ID, InterfaceUrls::circulationSettingsUrl,
      CIRCULATION_SETTINGS_PROPERTY);

  @Before
  public void beforeEach() {
    StorageTestSuite.cleanUpTable(TABLE_NAME);
  }

  @Test
  @SneakyThrows
  public void updateInsteadCreateWithTheSameName() {
    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = getCirculationSetting(id);
    JsonObject circulationSettingsResponse =
      circulationSettingsClient.create(circulationSettingsJson).getJson();
    JsonObject circulationSettingsJsonUpdated = getUpdatedSettingsJson();
    circulationSettingsClient.create(circulationSettingsJsonUpdated);
    JsonObject circulationSettingsById = circulationSettingsClient.getById(id).getJson();

    assertThatCorrectCreation(circulationSettingsResponse, circulationSettingsJson);
    assertThat(circulationSettingsClient.getAll().getTotalRecords(), is(1));
    assertThat(getValue(circulationSettingsJsonUpdated), is(getValue(circulationSettingsById)));
  }

  @Test
  @SneakyThrows
  public void canCreateAndRetrieveCirculationSettings() {
    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = getCirculationSetting(id);
    JsonObject circulationSettingsResponse =
      circulationSettingsClient.create(circulationSettingsJson).getJson();
    JsonObject circulationSettingsById = circulationSettingsClient.getById(id).getJson();

    assertThat(circulationSettingsResponse.getString(ID_KEY), is(id));
    assertThat(circulationSettingsById.getString(ID_KEY), is(id));
    assertThat(circulationSettingsById.getJsonObject(VALUE_KEY), is(
      circulationSettingsJson.getJsonObject(VALUE_KEY)));
  }

  @Test
  @SneakyThrows
  public void canUpdateCirculationSettings() {
    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = getCirculationSetting(id);
    circulationSettingsClient.create(circulationSettingsJson).getJson();
    circulationSettingsClient.attemptPutById(
      circulationSettingsJson.put(VALUE_KEY, new JsonObject().put(SAMPLE_KEY, "DONE")));
    JsonObject updatedCirculationSettings = circulationSettingsClient.getById(id).getJson();

    assertThat(updatedCirculationSettings.getString(ID_KEY), is(id));
    assertThat(updatedCirculationSettings.getJsonObject(VALUE_KEY), is(
      circulationSettingsJson.getJsonObject(VALUE_KEY)));
  }

  @Test
  @SneakyThrows
  public void canDeleteCirculationSettings() {
    UUID id = UUID.randomUUID();
    circulationSettingsClient.create(getCirculationSetting(id.toString())).getJson();
    circulationSettingsClient.deleteById(id);
    var deletedCirculationSettings = circulationSettingsClient.attemptGetById(id);
    assertThat(deletedCirculationSettings.getStatusCode(), is(NOT_FOUND_STATUS));
  }

  private static String getValue(JsonObject circulationSettingsById) {
    return circulationSettingsById.getJsonObject(VALUE_KEY).getString(SAMPLE_KEY);
  }

  private JsonObject getCirculationSetting(String id) {
    return new JsonObject()
      .put(ID_KEY, id)
      .put(NAME_KEY, SAMPLE_VALUE)
      .put(VALUE_KEY, new JsonObject().put(SAMPLE_KEY, INITIAL_VALUE));
  }

  private static void assertThatCorrectCreation(JsonObject circulationSettingsResponse,
    JsonObject circulationSettingsJson) {

    String actualCreatedId = circulationSettingsResponse.getString(ID_KEY);
    String expectedCreatedId = circulationSettingsJson.getString(ID_KEY);
    String actualCreatedName = circulationSettingsResponse.getString(NAME_KEY);
    String expectedCreatedName = circulationSettingsJson.getString(NAME_KEY);

    assertThat(actualCreatedId, is(expectedCreatedId));
    assertThat(actualCreatedName, is(expectedCreatedName));
  }

  private JsonObject getUpdatedSettingsJson() {
    String updatedId = UUID.randomUUID().toString();
    JsonObject circulationSettingsJsonUpdated = getCirculationSetting(updatedId);
    JsonObject updatedValue = new JsonObject().put(SAMPLE_KEY, UPDATED_VALUE);
    circulationSettingsJsonUpdated.put(VALUE_KEY, updatedValue);
    return circulationSettingsJsonUpdated;
  }
  @Test
  public void canCreateAndRetrieveEnableRequestPrintDetailsSetting() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {
    String id = UUID.randomUUID().toString();
    JsonObject enableRequestPrintDetailsSettingJson = new JsonObject();
    enableRequestPrintDetailsSettingJson.put("id", id);
    enableRequestPrintDetailsSettingJson.put("name", "Enable Request Print");
    enableRequestPrintDetailsSettingJson.put("value", new JsonObject().put("Enable Request Print", true));

    JsonObject circulationSettingsResponse =
      circulationSettingsClient.create(enableRequestPrintDetailsSettingJson).getJson();
    JsonObject circulationSettingsById = circulationSettingsClient.getById(id).getJson();

    assertThat(circulationSettingsResponse.getString("id"), is(id));
    assertThat(circulationSettingsResponse.getString("name"),
      is(enableRequestPrintDetailsSettingJson.getString("name")));
    assertThat(circulationSettingsById.getString("id"), is(id));
    assertThat(circulationSettingsById.getJsonObject("value"),
      is(enableRequestPrintDetailsSettingJson.getJsonObject("value")));
  }
}
