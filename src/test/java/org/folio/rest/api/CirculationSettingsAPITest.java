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
import org.folio.rest.support.spring.TestContextConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;

@RunWith(JUnitParamsRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class CirculationSettingsAPITest extends ApiTests {

  private final AssertingRecordClient circulationSettingsClient =
    new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::circulationSettingsUrl,
      "circulation-settings");

  @Test
  void canCreateCirculationSettings() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {
    String id = UUID.randomUUID().toString();
    JsonObject circulationSettingsJson = new JsonObject()
      .put("id", id)
      .put("name", "Sample settings")
      .put("value",
        new JsonObject().put("org.folio.circulation.settings", "true"));

    JsonObject circulationSettingsResponse =
      circulationSettingsClient.create(circulationSettingsJson).getJson();

    assertThat(circulationSettingsResponse.getString("id"), is(id));
  }

}
