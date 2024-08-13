package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CirculationSettingsAPITest extends ApiTests {
  private static final String TABLE_NAME = "circulation_settings";

  private final AssertingRecordClient circulationSettingsClient =
    new AssertingRecordClient(
      client, StorageTestSuite.TENANT_ID, InterfaceUrls::circulationSettingsUrl,
      "circulation-settings");

  @Before
  public void beforeEach() {
    StorageTestSuite.cleanUpTable(TABLE_NAME);
  }

  @Test
  public void updateInsteadCreateWithTheSameName() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = getCirculationSetting(id);
    JsonObject circulationSettingsResponse =
      circulationSettingsClient.create(circulationSettingsJson).getJson();
    String updatedId = UUID.randomUUID().toString();
    JsonObject circulationSettingsJsonUpdated = getCirculationSetting(updatedId);
    JsonObject updatedValue = new JsonObject().put("sample", "OK1");
    circulationSettingsJsonUpdated.put("value", updatedValue);
    circulationSettingsClient.create(circulationSettingsJsonUpdated);
    JsonObject circulationSettingsById = circulationSettingsClient.getById(id).getJson();

    assertThatCorrectCreation(circulationSettingsResponse, circulationSettingsJson);
    assertThat(circulationSettingsClient.getAll().getTotalRecords(), is(1));
    assertThat(getValue(circulationSettingsJsonUpdated), is(getValue(circulationSettingsById)));
  }

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

  private static String getValue(JsonObject circulationSettingsById) {
    return circulationSettingsById.getJsonObject("value")
      .getString("sample");
  }

  private JsonObject getCirculationSetting(String id) {
    return new JsonObject()
      .put("id", id)
      .put("name", "sample")
      .put("value", new JsonObject().put("sample", "OK"));
  }

  private static void assertThatCorrectCreation(JsonObject circulationSettingsResponse,
                                                JsonObject circulationSettingsJson) {

    String actualCreatedId = circulationSettingsResponse.getString("id");
    String expectedCreatedId = circulationSettingsJson.getString("id");
    String actualCreatedName = circulationSettingsResponse.getString("name");
    String expectedCreatedName = circulationSettingsJson.getString("name");

    assertThat(actualCreatedId, is(expectedCreatedId));
    assertThat(actualCreatedName, is(expectedCreatedName));
  }

}
