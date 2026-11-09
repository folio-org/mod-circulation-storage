package org.folio.rest.api;

import static java.lang.String.format;
import static org.folio.rest.support.http.InterfaceUrls.userLoanAnonymizationPreferencesUrl;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isBadRequest;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isInternalServerError;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isUnprocessableEntity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import lombok.SneakyThrows;

class UserLoanAnonymizationPreferencesApiTest extends ApiTests {
  private static final String EARLY = "asEarlyAsPossible";
  private static final String NEVER = "never";

  @BeforeEach
  void beforeEach() {
    truncateTables("user_loan_anonymization_preference");
  }

  @Test
  void replaceAddsUpdatesAndRemovesRows() {
    String unchanged = storedUser(NEVER);
    String changed = storedUser(EARLY);
    storedUser(NEVER);
    String added = randomUserId();

    assertThat(put(preferences(Map.of(unchanged, NEVER, changed, NEVER, added, EARLY))),
      isNoContent());
    assertThat(storedPreferences(), is(Map.of(unchanged, NEVER, changed, NEVER, added, EARLY)));
  }

  @Test
  void emptyListRemovesAllRows() {
    storedUser(NEVER);
    storedUser(EARLY);

    assertThat(put(preferences(Map.of())), isNoContent());
    assertThat(storedPreferences(), is(anEmptyMap()));
  }

  @Test
  void libraryPolicyIsRejectedAndNothingChanges() {
    String neverUser = storedUser(NEVER);

    assertThat(put(preferences(Map.of(randomUserId(), "libraryPolicy"))), isBadRequest());
    assertThat(storedPreferences(), is(Map.of(neverUser, NEVER)));
  }

  @Test
  void invalidUserIdIsRejectedAndNothingChanges() {
    String neverUser = storedUser(NEVER);

    assertThat(put(preferences(Map.of("not-a-uuid", EARLY))), isUnprocessableEntity());
    assertThat(storedPreferences(), is(Map.of(neverUser, NEVER)));
  }

  @Test
  void failedWriteKeepsTheOldRows() {
    String neverUser = storedUser(NEVER);
    String userId = randomUserId();
    JsonObject listingUserTwice = new JsonObject().put("preferences", new JsonArray()
      .add(new JsonObject().put("userId", userId).put("preference", NEVER))
      .add(new JsonObject().put("userId", userId).put("preference", EARLY)));

    assertThat(put(listingUserTwice), isInternalServerError());
    assertThat(storedPreferences(), is(Map.of(neverUser, NEVER)));
  }

  private static JsonObject preferences(Map<String, String> preferenceByUserId) {
    JsonArray preferences = new JsonArray();
    preferenceByUserId.forEach((userId, value) ->
      preferences.add(new JsonObject().put("userId", userId).put("preference", value)));
    return new JsonObject().put("preferences", preferences);
  }

  private static String storedUser(String preference) {
    String userId = randomUserId();
    waitFor(pgClient.execute(
      format("INSERT INTO %s.user_loan_anonymization_preference (id, jsonb) VALUES ($1, $2)",
        pgClient.getSchemaName()),
      Tuple.of(UUID.fromString(userId),
        new JsonObject().put("id", userId).put("preference", preference))));
    return userId;
  }

  private static Map<String, String> storedPreferences() {
    Map<String, String> preferences = new HashMap<>();
    waitFor(pgClient.execute(format(
        "SELECT id, jsonb->>'preference' FROM %s.user_loan_anonymization_preference",
        pgClient.getSchemaName())))
      .forEach(row -> preferences.put(row.getUUID(0).toString(), row.getString(1)));
    return preferences;
  }

  private static String randomUserId() {
    return UUID.randomUUID().toString();
  }

  @SneakyThrows
  private JsonResponse put(JsonObject body) {
    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture<>();
    client.put(userLoanAnonymizationPreferencesUrl(), body, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(putCompleted));
    return putCompleted.get(5, TimeUnit.SECONDS);
  }
}
