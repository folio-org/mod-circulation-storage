package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CirculationSettingsAPITest extends ApiTests {

  private final AssertingRecordClient circulationSettingsClient =
    new AssertingRecordClient(
      client, StorageTestSuite.TENANT_ID, InterfaceUrls::circulationSettingsUrl,
      "circulation-settings");

  @Test
  public void updateInsteadCreateWithTheSameName() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {
    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = getCirculationSetting(id);
    circulationSettingsClient.create(circulationSettingsJson);

    String id1 = UUID.randomUUID().toString();
    JsonObject circulationSettingsJsonUpdated = getCirculationSetting(id1);
    circulationSettingsJsonUpdated.put("value", new JsonObject().put("sample", "OK1"));
    circulationSettingsClient.create(circulationSettingsJsonUpdated);
    JsonObject circulationSettingsById = circulationSettingsClient.getById(id).getJson();

    assertThat(circulationSettingsClient.getAll().getTotalRecords(), is(1));
    assertThat("OK1", is(getValue(circulationSettingsById)));
  }

  @Test
  public void canCreateAndRetrieveCirculationSettings() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = getCirculationSetting(id);
    circulationSettingsJson.put("name", "sample2");
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

}
