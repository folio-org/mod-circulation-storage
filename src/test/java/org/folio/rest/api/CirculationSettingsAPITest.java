package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class CirculationSettingsAPITest extends ApiTests {

  private final AssertingRecordClient circulationSettingsClient =
    new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::circulationSettingsUrl,
      "circulation-settings");

  @Test
  public void canCreateAndRetrieveCirculationSettings() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = getCirculationSetting(id);
    JsonObject circulationSettingsResponse =
      circulationSettingsClient.create(circulationSettingsJson).getJson();
    JsonObject circulationSettingsById = circulationSettingsClient.getById(id).getJson();

    assertThat(circulationSettingsResponse.getString("id"), is(id));
    assertThat(circulationSettingsById.getString("id"), is(id));
    assertThat(circulationSettingsById.getJsonObject("value"), is(
      circulationSettingsJson.getJsonObject("value")));
  }

  @Test
  public void canUpdateCirculationSettings() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = getCirculationSetting(id);
    circulationSettingsClient.create(circulationSettingsJson).getJson();
    circulationSettingsClient.attemptPutById(
      circulationSettingsJson.put("value", new JsonObject().put("sample", "DONE")));
    JsonObject updatedCirculationSettings = circulationSettingsClient.getById(id).getJson();

    assertThat(updatedCirculationSettings.getString("id"), is(id));
    assertThat(updatedCirculationSettings.getJsonObject("value"), is(
      circulationSettingsJson.getJsonObject("value")));
  }

  @Test
  public void canDeleteCirculationSettings() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    UUID id = UUID.randomUUID();
    circulationSettingsClient.create(getCirculationSetting(id.toString())).getJson();
    circulationSettingsClient.deleteById(id);
    var deletedCirculationSettings = circulationSettingsClient.attemptGetById(id);
    assertThat(deletedCirculationSettings.getStatusCode(), is(404));
  }

  private JsonObject getCirculationSetting(String id) {
    return new JsonObject()
      .put("id", id)
      .put("name", "sample")
      .put("value", new JsonObject().put("sample", "OK"));
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
