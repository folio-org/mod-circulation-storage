package org.folio.rest.api;

import static org.folio.rest.persist.PostgresClient.getInstance;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertUpdateEventForCirculationRules;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesNoContent;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesOk;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesUnprocessableEntity;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CirculationRulesApiTest extends ApiTests {

  private static final String CIRCULATION_RULES_TABLE = "circulation_rules";
  private static final String DEFAULT_RULE_ID = "2d7589ab-a889-bb8e-e15a-1a65fe86cb22";

  @SneakyThrows
  @Before
  public void cleanUpCirculationRulesTable() {
    StorageTestSuite.cleanUpTable(CIRCULATION_RULES_TABLE);

    CirculationRules defaultRules = exampleRules();
    defaultRules.setId(DEFAULT_RULE_ID);
    defaultRules.setMetadata(new Metadata()
      .withCreatedDate(new Date())
      .withCreatedByUserId(randomId())
      .withUpdatedDate(new Date())
      .withUpdatedByUserId(randomId()));

    CompletableFuture<String> insertFuture = new CompletableFuture<>();
    getInstance(StorageTestSuite.getVertx(), StorageTestSuite.TENANT_ID)
      .save(CIRCULATION_RULES_TABLE, defaultRules.getId(), defaultRules, res -> {
        if (res.succeeded()) {
          insertFuture.complete(defaultRules.getId());
        } else {
          insertFuture.completeExceptionally(res.cause());
        }
      });
    insertFuture.get(5, TimeUnit.SECONDS);
  }

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
    assertThat(response, matchesOk());
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
    assertThat(response, matchesNoContent());
    return response.getBody();
  }

  public void putAndGet(CirculationRules circulationRules) throws Exception {
    JsonObject originalRules = get();
    var oldUpdatedDate = originalRules.getJsonObject("metadata").getString("updatedDate");
    put204(circulationRules);
    JsonObject updatedRules = get();
    assertThat(updatedRules.getString("rulesAsText"), is(circulationRules.getRulesAsText()));
    var metadata = updatedRules.getJsonObject("metadata");
    assertThat(metadata.getString("updatedByUserId"), is(not(nullValue())));
    assertThat(metadata.getString("updatedDate"), is(not(oldUpdatedDate)));
    assertUpdateEventForCirculationRules(originalRules, updatedRules);
  }

  @Test
  public void putAndGet() throws Exception {
    putAndGet(exampleRules());
    putAndGet(exampleRules2());
  }

  @Test
  public void putNullFields() throws Exception {
    CirculationRules circulationRules = new CirculationRules();
    assertThat(putResponse(circulationRules), matchesUnprocessableEntity());
  }
}
