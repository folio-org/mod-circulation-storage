package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  private static final String REQUEST_PRINT_SETTING = "Enable Request Print";

  private final AssertingRecordClient circulationSettingsClient =
    new AssertingRecordClient(
      client, StorageTestSuite.TENANT_ID, InterfaceUrls::circulationSettingsUrl,
      CIRCULATION_SETTINGS_PROPERTY);

  @BeforeEach
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

  @Test
  @SneakyThrows
  public void canCreateAndRetrieveEnableRequestPrintDetailsSetting() {
    String id = UUID.randomUUID().toString();
    JsonObject enableRequestPrintDetailsSettingJson = new JsonObject();
    enableRequestPrintDetailsSettingJson.put(ID_KEY, id);
    enableRequestPrintDetailsSettingJson.put(NAME_KEY, REQUEST_PRINT_SETTING);
    JsonObject enablePrintSettingJson = new JsonObject().put(REQUEST_PRINT_SETTING, true);
    enableRequestPrintDetailsSettingJson.put(VALUE_KEY, enablePrintSettingJson);

    JsonObject circulationSettingsResponse =
      circulationSettingsClient.create(enableRequestPrintDetailsSettingJson).getJson();
    JsonObject circulationSettingsById = circulationSettingsClient.getById(id).getJson();

    assertThat(circulationSettingsResponse.getString(ID_KEY), is(id));
    assertThat(circulationSettingsResponse.getString(NAME_KEY),
      is(enableRequestPrintDetailsSettingJson.getString(NAME_KEY)));
    assertThat(circulationSettingsById.getString(ID_KEY), is(id));
    assertThat(circulationSettingsById.getJsonObject(VALUE_KEY),
      is(enableRequestPrintDetailsSettingJson.getJsonObject(VALUE_KEY)));
  }

  private static String getValue(JsonObject circulationSettingsById) {
    return circulationSettingsById.getJsonObject(VALUE_KEY).getString(SAMPLE_KEY);
  }

  private JsonObject getCirculationSetting(String id) {
    var json = new JsonObject();
    json.put(ID_KEY, id);
    json.put(NAME_KEY, SAMPLE_VALUE);
    var value = new JsonObject();
    value.put(SAMPLE_KEY, INITIAL_VALUE);
    json.put(VALUE_KEY, value);
    return json;
  }

  private static void assertThatCorrectCreation(JsonObject response,
    JsonObject expected) {
    assertThat(response.getString(ID_KEY), is(expected.getString(ID_KEY)));
    assertThat(response.getString(NAME_KEY), is(expected.getString(NAME_KEY)));
    assertThat(response.getJsonObject(VALUE_KEY), is(expected.getJsonObject(VALUE_KEY)));
  }

  private static JsonObject getUpdatedSettingsJson() {
    String id = UUID.randomUUID().toString();
    var json = new JsonObject();
    json.put(ID_KEY, id);
    json.put(NAME_KEY, SAMPLE_VALUE);
    var value = new JsonObject();
    value.put(SAMPLE_KEY, UPDATED_VALUE);
    json.put(VALUE_KEY, value);
    return json;
  }
}
