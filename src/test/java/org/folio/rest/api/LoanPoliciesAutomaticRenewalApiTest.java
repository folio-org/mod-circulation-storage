package org.folio.rest.api;

import static org.folio.rest.support.builders.LoanPolicyRequestBuilder.defaultRollingPolicy;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesCreated;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesNoContent;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesNotFound;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesOk;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesUnprocessableEntity;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

class LoanPoliciesAutomaticRenewalApiTest extends ApiTests {
  private static final String LOAN_POLICIES_PATH = "/loan-policy-storage/loan-policies";
  private static final String AUTOMATIC_RENEWAL = "automaticRenewal";
  private static final String FIRST_ATTEMPT = "firstAutomaticRenewalAttemptDaysBeforeDueDate";
  private static final String RETRY_PERIOD = "automaticRenewalRetryPeriodInDays";

  @BeforeEach
  @SneakyThrows
  void beforeEach() {
    StorageTestSuite.deleteAll(StorageTestSuite.storageUrl(LOAN_POLICIES_PATH));
  }

  @AfterEach
  void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("loan_policy");
  }

  @Test
  void enabledConfigurationIsPersistedAndReturnedUnchanged() {
    UUID id = UUID.randomUUID();

    JsonObject created = create(enabledPolicy(id, 3, 1));
    assertAutomaticRenewal(created, 3, 1);
    assertAutomaticRenewal(get(id).getJson(), 3, 1);
    assertAutomaticRenewal(getFromCollection(id), 3, 1);

    JsonResponse putResponse = put(id, enabledPolicy(id, 5, 0));
    assertThat(putResponse.getBody(), putResponse, matchesNoContent());
    assertAutomaticRenewal(get(id).getJson(), 5, 0);
    assertAutomaticRenewal(getFromCollection(id), 5, 0);
  }

  @ParameterizedTest
  @CsvSource({ "0, 0", "100, 100", "0, 100", "100, 0" })
  void boundaryTimingValuesAreAccepted(int firstAttempt, int retryPeriod) {
    UUID id = UUID.randomUUID();

    assertAutomaticRenewal(create(enabledPolicy(id, firstAttempt, retryPeriod)),
      firstAttempt, retryPeriod);
    assertAutomaticRenewal(get(id).getJson(), firstAttempt, retryPeriod);
    assertAutomaticRenewal(getFromCollection(id), firstAttempt, retryPeriod);
  }

  @ParameterizedTest
  @CsvSource({
    "firstAutomaticRenewalAttemptDaysBeforeDueDate, -1, must be greater than or equal to 0",
    "firstAutomaticRenewalAttemptDaysBeforeDueDate, 101, must be less than or equal to 100",
    "automaticRenewalRetryPeriodInDays, -1, must be greater than or equal to 0",
    "automaticRenewalRetryPeriodInDays, 101, must be less than or equal to 100"
  })
  void outOfRangeTimingValuesAreRejected(String field, int value, String message) {
    JsonObject policy = enabledPolicy(UUID.randomUUID(), 3, 1);
    renewalsPolicy(policy).put(field, value);

    assertRejectedOnPostAndPut(policy, "renewalsPolicy." + field,
      String.valueOf(value), message);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "firstAutomaticRenewalAttemptDaysBeforeDueDate",
    "automaticRenewalRetryPeriodInDays"
  })
  void missingTimingValueIsRejectedWhenEnabled(String field) {
    JsonObject policy = enabledPolicy(UUID.randomUUID(), 3, 1);
    renewalsPolicy(policy).remove(field);

    assertRejectedOnPostAndPut(policy, "renewalsPolicy." + field, "null",
      field + " is required when automaticRenewal is true");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "firstAutomaticRenewalAttemptDaysBeforeDueDate",
    "automaticRenewalRetryPeriodInDays"
  })
  void nullTimingValueIsRejectedWhenEnabled(String field) {
    JsonObject policy = enabledPolicy(UUID.randomUUID(), 3, 1);
    renewalsPolicy(policy).putNull(field);

    assertRejectedOnPostAndPut(policy, "renewalsPolicy." + field, "null",
      field + " is required when automaticRenewal is true");
  }

  @Test
  void automaticRenewalIsRejectedWhenNotRenewable() {
    JsonObject policy = enabledPolicy(UUID.randomUUID(), 3, 1).put("renewable", false);

    assertRejectedOnPostAndPut(policy, "renewalsPolicy.automaticRenewal", "true",
      "Automatic renewal settings are not allowed when renewable is false");
  }

  @Test
  void disabledToggleIsRejectedWhenNotRenewable() {
    JsonObject policy = defaultRollingPolicy().create().put("renewable", false);
    renewalsPolicy(policy).put(AUTOMATIC_RENEWAL, false);

    assertRejectedOnPostAndPut(policy, "renewalsPolicy.automaticRenewal", "false",
      "Automatic renewal settings are not allowed when renewable is false");
  }

  @Test
  void timingValueIsRejectedWhenNotRenewable() {
    JsonObject policy = defaultRollingPolicy().create().put("renewable", false);
    renewalsPolicy(policy).put(RETRY_PERIOD, 0);

    assertRejectedOnPostAndPut(policy, "renewalsPolicy." + RETRY_PERIOD, "0",
      "Automatic renewal settings are not allowed when renewable is false");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "firstAutomaticRenewalAttemptDaysBeforeDueDate",
    "automaticRenewalRetryPeriodInDays"
  })
  void orphanedTimingValueIsRejectedWhenToggleIsAbsent(String field) {
    JsonObject policy = defaultRollingPolicy().create();
    renewalsPolicy(policy).put(field, 2);

    assertRejectedOnPostAndPut(policy, "renewalsPolicy." + field, "2",
      field + " is not allowed when automaticRenewal is not set");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "firstAutomaticRenewalAttemptDaysBeforeDueDate",
    "automaticRenewalRetryPeriodInDays"
  })
  void orphanedTimingValueIsRejectedWhenToggleIsFalse(String field) {
    JsonObject policy = defaultRollingPolicy().create();
    renewalsPolicy(policy).put(AUTOMATIC_RENEWAL, false).put(field, 2);

    assertRejectedOnPostAndPut(policy, "renewalsPolicy." + field, "2",
      field + " is not allowed when automaticRenewal is false");
  }

  @Test
  void legacyPolicyStaysWithoutAutomaticRenewalFields() {
    UUID id = UUID.randomUUID();
    JsonObject policy = defaultRollingPolicy().withId(id).create();

    assertNoAutomaticRenewalFields(create(policy));
    assertNoAutomaticRenewalFields(get(id).getJson());
    assertNoAutomaticRenewalFields(getFromCollection(id));

    JsonResponse putResponse = put(id, policy.put("description", "updated"));
    assertThat(putResponse.getBody(), putResponse, matchesNoContent());

    JsonObject updated = get(id).getJson();
    assertThat(updated.getString("description"), is("updated"));
    assertNoAutomaticRenewalFields(updated);
    assertNoAutomaticRenewalFields(getFromCollection(id));
  }

  @Test
  void disablingAutomaticRenewalRemovesTimingValues() {
    UUID id = UUID.randomUUID();
    create(enabledPolicy(id, 3, 1));

    JsonObject disabled = enabledPolicy(id, 3, 1);
    JsonObject disabledRenewalsPolicy = renewalsPolicy(disabled);
    disabledRenewalsPolicy.put(AUTOMATIC_RENEWAL, false);
    disabledRenewalsPolicy.remove(FIRST_ATTEMPT);
    disabledRenewalsPolicy.remove(RETRY_PERIOD);

    JsonResponse putResponse = put(id, disabled);
    assertThat(putResponse.getBody(), putResponse, matchesNoContent());

    for (JsonObject stored : List.of(get(id).getJson(), getFromCollection(id))) {
      JsonObject renewalsPolicy = renewalsPolicy(stored);
      assertThat(stored.encodePrettily(), renewalsPolicy.getBoolean(AUTOMATIC_RENEWAL), is(false));
      assertThat(stored.encodePrettily(), renewalsPolicy.containsKey(FIRST_ATTEMPT), is(false));
      assertThat(stored.encodePrettily(), renewalsPolicy.containsKey(RETRY_PERIOD), is(false));
    }
  }

  @Test
  void disablingRenewalsRemovesAutomaticRenewalConfiguration() {
    UUID id = UUID.randomUUID();
    create(enabledPolicy(id, 3, 1));

    JsonObject notRenewable = defaultRollingPolicy().withId(id).create()
      .put("renewable", false);

    JsonResponse putResponse = put(id, notRenewable);
    assertThat(putResponse.getBody(), putResponse, matchesNoContent());

    for (JsonObject stored : List.of(get(id).getJson(), getFromCollection(id))) {
      assertThat(stored.encodePrettily(), stored.getBoolean("renewable"), is(false));
      assertNoAutomaticRenewalFields(stored);
    }
  }

  @Test
  void canQueryByAutomaticRenewal() {
    UUID enabledId = UUID.randomUUID();
    UUID disabledId = UUID.randomUUID();
    UUID absentId = UUID.randomUUID();

    create(enabledPolicy(enabledId, 3, 1));
    JsonObject disabled = defaultRollingPolicy().withId(disabledId).create();
    renewalsPolicy(disabled).put(AUTOMATIC_RENEWAL, false);
    create(disabled);
    create(defaultRollingPolicy().withId(absentId).create());

    assertThat(queryIds("renewalsPolicy.automaticRenewal==true"),
      contains(enabledId.toString()));
    assertThat(queryIds("renewalsPolicy.automaticRenewal==false"),
      contains(disabledId.toString()));
  }

  private static void assertNoAutomaticRenewalFields(JsonObject policy) {
    JsonObject renewalsPolicy = renewalsPolicy(policy);
    if (renewalsPolicy == null) {
      return;
    }
    assertThat(policy.encodePrettily(), renewalsPolicy.containsKey(AUTOMATIC_RENEWAL), is(false));
    assertThat(policy.encodePrettily(), renewalsPolicy.containsKey(FIRST_ATTEMPT), is(false));
    assertThat(policy.encodePrettily(), renewalsPolicy.containsKey(RETRY_PERIOD), is(false));
  }

  private static JsonObject enabledPolicy(UUID id, int firstAttempt, int retryPeriod) {
    JsonObject policy = defaultRollingPolicy().withId(id).create();
    renewalsPolicy(policy)
      .put(AUTOMATIC_RENEWAL, true)
      .put(FIRST_ATTEMPT, firstAttempt)
      .put(RETRY_PERIOD, retryPeriod);
    return policy;
  }

  private static JsonObject renewalsPolicy(JsonObject policy) {
    return policy.getJsonObject("renewalsPolicy");
  }

  private static void assertAutomaticRenewal(JsonObject policy, int firstAttempt,
    int retryPeriod) {

    JsonObject renewalsPolicy = renewalsPolicy(policy);
    assertThat(policy.encodePrettily(), renewalsPolicy.getBoolean(AUTOMATIC_RENEWAL), is(true));
    assertThat(policy.encodePrettily(), renewalsPolicy.getInteger(FIRST_ATTEMPT), is(firstAttempt));
    assertThat(policy.encodePrettily(), renewalsPolicy.getInteger(RETRY_PERIOD), is(retryPeriod));
  }

  private static void assertValidationError(JsonResponse response, String key,
    String value, String message) {

    assertThat(response.getBody(), response, matchesUnprocessableEntity());
    assertThat(response.getJson(),
      hasErrorWith(allOf(hasParameter(key, value), hasMessage(message))));
  }

  /**
   * Asserts that the policy is rejected by POST (and nothing is stored) and by
   * a PUT replacing an existing enabled policy (which stays unchanged).
   */
  private void assertRejectedOnPostAndPut(JsonObject invalidPolicy, String key,
    String value, String message) {

    UUID postId = UUID.randomUUID();
    JsonObject postRequest = invalidPolicy.copy().put("id", postId.toString());
    assertValidationError(post(postRequest), key, value, message);
    assertThat(get(postId), matchesNotFound());

    UUID putId = UUID.randomUUID();
    create(enabledPolicy(putId, 3, 1));
    JsonObject putRequest = invalidPolicy.copy().put("id", putId.toString());
    assertValidationError(put(putId, putRequest), key, value, message);
    assertAutomaticRenewal(get(putId).getJson(), 3, 1);
  }

  private JsonObject create(JsonObject policy) {
    JsonResponse response = post(policy);
    assertThat(response.getBody(), response, matchesCreated());
    return response.getJson();
  }

  private JsonObject getFromCollection(UUID id) {
    JsonResponse response = getCollection("id==" + id);
    List<JsonObject> policies = JsonArrayHelper.toList(response.getJson(), "loanPolicies");
    assertThat(response.getBody(), policies.size(), is(1));
    return policies.get(0);
  }

  private List<String> queryIds(String cql) {
    return JsonArrayHelper.toList(getCollection(cql).getJson(), "loanPolicies").stream()
      .map(policy -> policy.getString("id"))
      .toList();
  }

  @SneakyThrows
  private JsonResponse getCollection(String cql) {
    URL url = StorageTestSuite.storageUrl(LOAN_POLICIES_PATH, "query", cql);
    CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(completed));
    JsonResponse response = completed.get(5, TimeUnit.SECONDS);
    assertThat(response.getBody(), response, matchesOk());
    return response;
  }

  @SneakyThrows
  private JsonResponse post(JsonObject policy) {
    CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.post(StorageTestSuite.storageUrl(LOAN_POLICIES_PATH), policy,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(completed));
    return completed.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private JsonResponse put(UUID id, JsonObject policy) {
    CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.put(StorageTestSuite.storageUrl(LOAN_POLICIES_PATH + "/" + id), policy,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(completed));
    return completed.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private JsonResponse get(UUID id) {
    CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.get(StorageTestSuite.storageUrl(LOAN_POLICIES_PATH + "/" + id),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(completed));
    return completed.get(5, TimeUnit.SECONDS);
  }
}
