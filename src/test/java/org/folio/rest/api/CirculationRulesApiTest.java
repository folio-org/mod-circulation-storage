package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CirculationRulesApiTest extends ApiTests {
  private static final int HTTP_VALIDATION_ERROR = 422;
  private String uuid;

  public static URL rulesStorageUrl() throws MalformedURLException {
    return rulesStorageUrl("");
  }

  private static URL rulesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/circulation-rules-storage" + subPath);
  }

  private CirculationRules circulationRules(String rulesAsText) {
    CirculationRules circulationRules = new CirculationRules();
    circulationRules.setRulesAsText(rulesAsText);
    return circulationRules;
  }

  private CirculationRules exampleRules() {
    return circulationRules("fallback-policy: no-circulation\npriority: t, a, b, c, s, m, g\nm book cd dvd: in-house\n");
  }

  private CirculationRules exampleRules2() {
    return circulationRules("fallback-policy: no-circulation\npriority: t, a, b, c, s, m, g\nm book cd dvd: general-loan\n");
  }

  private JsonResponse getResponse() throws Exception {
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.get(rulesStorageUrl(),
      null, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));
    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  /** @return the JSON of the get response, asserts an HTTP_OK=200 response */
  private JsonObject get() throws Exception {
    JsonResponse response = getResponse();
    assertThat(response.getBody(), response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    return response.getJson();
  }

  private JsonResponse putResponse(CirculationRules circulationRules) throws Exception {
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.put(rulesStorageUrl(),
    circulationRules, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));
    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  /** @return the message of the put response, asserts an HTTP_OK=200 response */
  private String put204(CirculationRules circulationRules) throws Exception {
    JsonResponse response = putResponse(circulationRules);
    assertThat(response.getBody(), response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    return response.getBody();
  }

  public void putAndGet(CirculationRules circulationRules) throws Exception {
    put204(circulationRules);
    JsonObject json = get();
    assertThat(json.getString("rulesAsText"), is(circulationRules.getRulesAsText()));
  }

  @Test
  public void putAndGet() throws Exception {
    putAndGet(exampleRules());
    putAndGet(exampleRules2());
  }

  @Test
  public void putNullFields() throws Exception {
    CirculationRules circulationRules = new CirculationRules();
    assertThat(putResponse(circulationRules).getStatusCode(), is(HTTP_VALIDATION_ERROR));
  }
}
