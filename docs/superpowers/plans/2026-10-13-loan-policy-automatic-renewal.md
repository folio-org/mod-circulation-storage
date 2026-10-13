# Loan Policy Automatic Renewal (CIRCSTORE-680) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist an optional automatic-renewal configuration in `renewalsPolicy` of Loan Policies, with range and cross-field validation on POST and PUT, CQL filtering, and a `loan-policy-storage` 2.5 interface bump.

**Architecture:**
- **Ranges:** the JSON schema declares them; RMB Bean Validation enforces them (HTTP 422).
- **Cross-field rules:** a new stateless `LoanPolicyAutomaticRenewalValidator`, called at the end of the existing `LoanPoliciesAPI.validate(...)`, so POST and PUT share it.
- **Persistence, PUT replacement and CQL:** existing JSONB behaviour, no code change; covered by API tests.

**Tech Stack:** Java 21, RMB 36.0.0 (jsonschema2pojo models from `ramls/`), Vert.x 5, PostgreSQL (testcontainers), JUnit 5 + Hamcrest, Lombok, Maven.

**Spec:** `docs/superpowers/specs/2026-10-13-loan-policy-automatic-renewal-design.md`

## Global Constraints

- **Formatting:** two-space indentation for Java; follow surrounding style in JSON/RAML files.
- **Field names**, all inside `renewalsPolicy`:
  - `automaticRenewal`: boolean, no `default`
  - `firstAutomaticRenewalAttemptDaysBeforeDueDate`: integer, `minimum: 0`, `maximum: 100`
  - `automaticRenewalRetryPeriodInDays`: integer, `minimum: 0`, `maximum: 100`
- **Error parameter keys:** `renewalsPolicy.<field>`.
- **Cross-field error messages:** exact text, `<field>` is the bare field name:
  - `Automatic renewal settings are not allowed when renewable is false`
  - `<field> is not allowed when automaticRenewal is not set`
  - `<field> is not allowed when automaticRenewal is false`
  - `<field> is required when automaticRenewal is true`
- **Interface version:** `loan-policy-storage` 2.4 -> 2.5.
- **No changes to:** Jackson deserialization (type coercion stays default), DB schema/indexes, reference data.
- **Maven:** runs offline (`-o`); the local repository is configured in `~/.m2/settings.xml`. API test classes run individually with `-Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`; this starts Postgres and Kafka testcontainers (Docker is available) and takes ~1 min.
- **Commit messages:** `CIRCSTORE-680: <description>`.

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `ramls/loan-policy.json` | Modify | Add the three fields to `renewalsPolicy` |
| `src/main/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidator.java` | Create | Cross-field rules, returns `Optional<Errors>` |
| `src/main/java/org/folio/rest/impl/LoanPoliciesAPI.java` | Modify | Call the validator at the end of `validate(...)` |
| `src/test/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidatorTest.java` | Create | Unit tests for every rule |
| `src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java` | Create | API tests for Scenarios 1-8, 10-13 |
| `src/test/java/org/folio/rest/api/StorageTestSuite.java` | Modify | Register the new API test class |
| `ramls/loan-policy-storage.raml` | Modify | `version: v2.5` |
| `descriptors/ModuleDescriptor-template.json` | Modify | `loan-policy-storage` provides `2.5` |
| `ramls/examples/loan-policy.json`, `ramls/examples/loan-policies.json` | Modify | Show the new fields |
| `NEWS.md` | Modify | Release note |

---

### Task 1: Schema fields with range validation

**Files:**
- Modify: `ramls/loan-policy.json:99-103`
- Create: `src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java`
- Modify: `src/test/java/org/folio/rest/api/StorageTestSuite.java:61`

**Interfaces:**
- Produces:
  - Generated model `org.folio.rest.jaxrs.model.RenewalsPolicy` with these accessors (used by Tasks 2-3):
    - `Boolean getAutomaticRenewal()` / `RenewalsPolicy withAutomaticRenewal(Boolean)`
    - `Integer getFirstAutomaticRenewalAttemptDaysBeforeDueDate()` / `withFirstAutomaticRenewalAttemptDaysBeforeDueDate(Integer)`
    - `Integer getAutomaticRenewalRetryPeriodInDays()` / `withAutomaticRenewalRetryPeriodInDays(Integer)`
  - Test class `LoanPoliciesAutomaticRenewalApiTest`. Tasks 3 and 4 add test methods to it and use these helpers:
    - `JsonObject enabledPolicy(UUID id, int firstAttempt, int retryPeriod)`
    - `JsonObject renewalsPolicy(JsonObject policy)`
    - `JsonResponse post(JsonObject)`, `JsonResponse put(UUID, JsonObject)`, `JsonResponse get(UUID)`
    - `JsonObject create(JsonObject)` (asserts 201)
    - `JsonObject getFromCollection(UUID)`
    - `List<String> queryIds(String cql)`
    - `void assertValidationError(JsonResponse, String key, String value, String message)`
    - `void assertRejectedOnPostAndPut(JsonObject invalidPolicy, String key, String value, String message)`
    - Constants `AUTOMATIC_RENEWAL`, `FIRST_ATTEMPT`, `RETRY_PERIOD` (bare field names)

- [ ] **Step 1: Create the API test class with helpers, round-trip, boundary and range tests**

Create `src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java`:

```java
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
```

Note: `contains` and `ValueSource` are imported now but first used in Tasks 3-4. Leave the unused-import warnings until then.

- [ ] **Step 2: Register the class in the suite**

In `src/test/java/org/folio/rest/api/StorageTestSuite.java`, change:

```java
  LoanPoliciesApiTest.class,
```

to:

```java
  LoanPoliciesApiTest.class,
  LoanPoliciesAutomaticRenewalApiTest.class,
```

- [ ] **Step 3: Run the tests to verify that the range tests fail**

Run:
```bash
mvn -o test -Dtest='LoanPoliciesAutomaticRenewalApiTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Expected:
- `outOfRangeTimingValuesAreRejected` fails all 4 cases: 201 instead of 422, because `renewalsPolicy` currently accepts unknown properties.
- The round-trip and boundary tests may already pass for the same reason. That is fine.

- [ ] **Step 4: Add the fields to the schema**

In `ramls/loan-policy.json`, replace:

```json
        "alternateFixedDueDateScheduleId": {
          "type": "string",
          "description": "Alternate fixed due date schedule (due date limit) for renewals"
        }
      }
    },
```

with:

```json
        "alternateFixedDueDateScheduleId": {
          "type": "string",
          "description": "Alternate fixed due date schedule (due date limit) for renewals"
        },
        "automaticRenewal": {
          "type": "boolean",
          "description": "Whether automatic renewal is enabled; absent means disabled. Requires renewable to be true"
        },
        "firstAutomaticRenewalAttemptDaysBeforeDueDate": {
          "type": "integer",
          "minimum": 0,
          "maximum": 100,
          "description": "Number of days before the due date when the first automatic renewal attempt occurs; 0 means on the due date. Required if and only if automaticRenewal is true"
        },
        "automaticRenewalRetryPeriodInDays": {
          "type": "integer",
          "minimum": 0,
          "maximum": 100,
          "description": "Retry period in days for automatic renewal attempts. Required if and only if automaticRenewal is true"
        }
      }
    },
```

Do NOT add `"default"` to `automaticRenewal`. It would add `automaticRenewal: false` to every newly saved record.

- [ ] **Step 5: Run the tests to verify that they pass**

Run:
```bash
mvn -o test -Dtest='LoanPoliciesAutomaticRenewalApiTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `Tests run: 9, Failures: 0, Errors: 0` and BUILD SUCCESS.

Also confirm that the generated model has the bounds:
```bash
grep -n -B3 "private Integer firstAutomaticRenewalAttemptDaysBeforeDueDate\|private Integer automaticRenewalRetryPeriodInDays\|private Boolean automaticRenewal;" target/generated-sources/raml-jaxrs/org/folio/rest/jaxrs/model/RenewalsPolicy.java
```

Expected: each integer field is preceded by `@DecimalMin("0")` and `@DecimalMax("100")`, and `private Boolean automaticRenewal;` has no initializer.

- [ ] **Step 6: Commit**

```bash
git add ramls/loan-policy.json src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java src/test/java/org/folio/rest/api/StorageTestSuite.java
git commit -m "CIRCSTORE-680: Add automatic renewal fields to loan policy schema"
```

---

### Task 2: Cross-field validator (unit level)

**Files:**
- Create: `src/main/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidator.java`
- Test: `src/test/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidatorTest.java`

**Interfaces:**
- Consumes: the `RenewalsPolicy` accessors from Task 1; `LoanPolicy.getRenewable()` / `getRenewalsPolicy()`; `org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(String field, String value, String message)` (RMB, returns `Errors` holding one `Error`).
- Produces: `public class LoanPolicyAutomaticRenewalValidator` with a public no-arg constructor and `public Optional<Errors> validate(LoanPolicy loanPolicy)`. It returns `Optional.empty()` when the policy is valid, otherwise an `Errors` with exactly one `Error`. Task 3 uses it.

- [ ] **Step 1: Write the failing unit tests**

Create `src/test/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidatorTest.java`:

```java
package org.folio.service.policy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Optional;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.model.RenewalsPolicy;
import org.junit.jupiter.api.Test;

class LoanPolicyAutomaticRenewalValidatorTest {
  private static final String AUTOMATIC_RENEWAL_KEY = "renewalsPolicy.automaticRenewal";
  private static final String FIRST_ATTEMPT_KEY =
    "renewalsPolicy.firstAutomaticRenewalAttemptDaysBeforeDueDate";
  private static final String RETRY_PERIOD_KEY =
    "renewalsPolicy.automaticRenewalRetryPeriodInDays";
  private static final String NOT_RENEWABLE_MESSAGE =
    "Automatic renewal settings are not allowed when renewable is false";

  private final LoanPolicyAutomaticRenewalValidator validator =
    new LoanPolicyAutomaticRenewalValidator();

  @Test
  void policyWithoutRenewalsPolicyIsValid() {
    LoanPolicy policy = new LoanPolicy().withName("p").withLoanable(true).withRenewable(false);

    assertThat(validator.validate(policy).isPresent(), is(false));
  }

  @Test
  void legacyRenewablePolicyIsValid() {
    assertThat(validator.validate(policy(true, null, null, null)).isPresent(), is(false));
  }

  @Test
  void legacyNonRenewablePolicyIsValid() {
    assertThat(validator.validate(policy(false, null, null, null)).isPresent(), is(false));
  }

  @Test
  void enabledPolicyWithBothTimingsIsValid() {
    assertThat(validator.validate(policy(true, true, 3, 1)).isPresent(), is(false));
  }

  @Test
  void enabledPolicyWithZeroTimingsIsValid() {
    assertThat(validator.validate(policy(true, true, 0, 0)).isPresent(), is(false));
  }

  @Test
  void enabledPolicyWithMaximumTimingsIsValid() {
    assertThat(validator.validate(policy(true, true, 100, 100)).isPresent(), is(false));
  }

  @Test
  void disabledPolicyWithoutTimingsIsValid() {
    assertThat(validator.validate(policy(true, false, null, null)).isPresent(), is(false));
  }

  @Test
  void toggleIsRejectedWhenNotRenewable() {
    assertSingleError(validator.validate(policy(false, true, 3, 1)),
      AUTOMATIC_RENEWAL_KEY, "true", NOT_RENEWABLE_MESSAGE);
  }

  @Test
  void disabledToggleIsRejectedWhenNotRenewable() {
    assertSingleError(validator.validate(policy(false, false, null, null)),
      AUTOMATIC_RENEWAL_KEY, "false", NOT_RENEWABLE_MESSAGE);
  }

  @Test
  void firstAttemptIsRejectedWhenNotRenewable() {
    assertSingleError(validator.validate(policy(false, null, 3, null)),
      FIRST_ATTEMPT_KEY, "3", NOT_RENEWABLE_MESSAGE);
  }

  @Test
  void retryPeriodIsRejectedWhenNotRenewable() {
    assertSingleError(validator.validate(policy(false, null, null, 0)),
      RETRY_PERIOD_KEY, "0", NOT_RENEWABLE_MESSAGE);
  }

  @Test
  void firstAttemptIsRejectedWhenToggleIsAbsent() {
    assertSingleError(validator.validate(policy(true, null, 3, null)),
      FIRST_ATTEMPT_KEY, "3",
      "firstAutomaticRenewalAttemptDaysBeforeDueDate is not allowed when automaticRenewal is not set");
  }

  @Test
  void retryPeriodIsRejectedWhenToggleIsAbsent() {
    assertSingleError(validator.validate(policy(true, null, null, 2)),
      RETRY_PERIOD_KEY, "2",
      "automaticRenewalRetryPeriodInDays is not allowed when automaticRenewal is not set");
  }

  @Test
  void firstAttemptIsRejectedWhenToggleIsFalse() {
    assertSingleError(validator.validate(policy(true, false, 3, 1)),
      FIRST_ATTEMPT_KEY, "3",
      "firstAutomaticRenewalAttemptDaysBeforeDueDate is not allowed when automaticRenewal is false");
  }

  @Test
  void retryPeriodIsRejectedWhenToggleIsFalse() {
    assertSingleError(validator.validate(policy(true, false, null, 1)),
      RETRY_PERIOD_KEY, "1",
      "automaticRenewalRetryPeriodInDays is not allowed when automaticRenewal is false");
  }

  @Test
  void firstAttemptIsRequiredWhenToggleIsTrue() {
    assertSingleError(validator.validate(policy(true, true, null, 1)),
      FIRST_ATTEMPT_KEY, "null",
      "firstAutomaticRenewalAttemptDaysBeforeDueDate is required when automaticRenewal is true");
  }

  @Test
  void retryPeriodIsRequiredWhenToggleIsTrue() {
    assertSingleError(validator.validate(policy(true, true, 3, null)),
      RETRY_PERIOD_KEY, "null",
      "automaticRenewalRetryPeriodInDays is required when automaticRenewal is true");
  }

  @Test
  void firstAttemptIsReportedFirstWhenBothTimingsAreMissing() {
    assertSingleError(validator.validate(policy(true, true, null, null)),
      FIRST_ATTEMPT_KEY, "null",
      "firstAutomaticRenewalAttemptDaysBeforeDueDate is required when automaticRenewal is true");
  }

  private static LoanPolicy policy(boolean renewable, Boolean automaticRenewal,
    Integer firstAttempt, Integer retryPeriod) {

    return new LoanPolicy()
      .withName("p")
      .withLoanable(true)
      .withRenewable(renewable)
      .withRenewalsPolicy(new RenewalsPolicy()
        .withAutomaticRenewal(automaticRenewal)
        .withFirstAutomaticRenewalAttemptDaysBeforeDueDate(firstAttempt)
        .withAutomaticRenewalRetryPeriodInDays(retryPeriod));
  }

  private static void assertSingleError(Optional<Errors> result, String key, String value,
    String message) {

    assertThat(result.isPresent(), is(true));
    List<Error> errors = result.get().getErrors();
    assertThat(errors.size(), is(1));
    Error error = errors.get(0);
    assertThat(error.getMessage(), is(message));
    assertThat(error.getParameters().size(), is(1));
    assertThat(error.getParameters().get(0).getKey(), is(key));
    assertThat(error.getParameters().get(0).getValue(), is(value));
  }
}
```

- [ ] **Step 2: Run the tests to verify that they fail**

Run:
```bash
mvn -o test -Dtest='LoanPolicyAutomaticRenewalValidatorTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: COMPILATION ERROR, `cannot find symbol: class LoanPolicyAutomaticRenewalValidator`.

- [ ] **Step 3: Implement the validator**

Create `src/main/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidator.java`:

```java
package org.folio.service.policy;

import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.model.RenewalsPolicy;

/**
 * Validates the cross-field rules of the automatic renewal settings in the
 * {@code renewalsPolicy} of a loan policy. Value ranges of the timing fields
 * are enforced by the JSON schema.
 */
public class LoanPolicyAutomaticRenewalValidator {
  private static final String KEY_PREFIX = "renewalsPolicy.";
  private static final String AUTOMATIC_RENEWAL = "automaticRenewal";
  private static final String FIRST_ATTEMPT = "firstAutomaticRenewalAttemptDaysBeforeDueDate";
  private static final String RETRY_PERIOD = "automaticRenewalRetryPeriodInDays";

  /**
   * @return the first rule violation, or empty if the settings are valid
   */
  public Optional<Errors> validate(LoanPolicy loanPolicy) {
    RenewalsPolicy renewalsPolicy = loanPolicy.getRenewalsPolicy();
    if (renewalsPolicy == null) {
      return Optional.empty();
    }

    Setting automaticRenewal = new Setting(AUTOMATIC_RENEWAL,
      renewalsPolicy.getAutomaticRenewal());
    List<Setting> timings = List.of(
      new Setting(FIRST_ATTEMPT, renewalsPolicy.getFirstAutomaticRenewalAttemptDaysBeforeDueDate()),
      new Setting(RETRY_PERIOD, renewalsPolicy.getAutomaticRenewalRetryPeriodInDays()));

    if (!Boolean.TRUE.equals(loanPolicy.getRenewable())) {
      return firstPresent(Stream.concat(Stream.of(automaticRenewal), timings.stream()))
        .map(setting -> error(setting,
          "Automatic renewal settings are not allowed when renewable is false"));
    }

    if (!automaticRenewal.isPresent()) {
      return firstPresent(timings.stream())
        .map(setting -> error(setting,
          setting.name() + " is not allowed when automaticRenewal is not set"));
    }

    if (Boolean.FALSE.equals(automaticRenewal.value())) {
      return firstPresent(timings.stream())
        .map(setting -> error(setting,
          setting.name() + " is not allowed when automaticRenewal is false"));
    }

    return timings.stream()
      .filter(setting -> !setting.isPresent())
      .findFirst()
      .map(setting -> error(setting,
        setting.name() + " is required when automaticRenewal is true"));
  }

  private static Optional<Setting> firstPresent(Stream<Setting> settings) {
    return settings.filter(Setting::isPresent).findFirst();
  }

  private static Errors error(Setting setting, String message) {
    return createValidationErrorMessage(KEY_PREFIX + setting.name(),
      String.valueOf(setting.value()), message);
  }

  private record Setting(String name, Object value) {
    boolean isPresent() {
      return value != null;
    }
  }
}
```

- [ ] **Step 4: Run the tests to verify that they pass**

Run:
```bash
mvn -o test -Dtest='LoanPolicyAutomaticRenewalValidatorTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `Tests run: 18, Failures: 0, Errors: 0` and BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidator.java src/test/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidatorTest.java
git commit -m "CIRCSTORE-680: Add loan policy automatic renewal validator"
```

---

### Task 3: Enforce the cross-field rules on POST and PUT

**Files:**
- Modify: `src/main/java/org/folio/rest/impl/LoanPoliciesAPI.java` (imports, constants at lines 24-25, end of `validate(...)` at lines 199-201)
- Test: `src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java`

**Interfaces:**
- Consumes:
  - `LoanPolicyAutomaticRenewalValidator#validate(LoanPolicy): Optional<Errors>` (Task 2)
  - The test helpers `enabledPolicy`, `renewalsPolicy`, `assertRejectedOnPostAndPut` and the field constants (Task 1)
- Produces: POST/PUT respond 422 with the validator's `Errors` body.

- [ ] **Step 1: Add the failing API tests**

Add these methods to `LoanPoliciesAutomaticRenewalApiTest`, after `outOfRangeTimingValuesAreRejected`:

```java
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
```

- [ ] **Step 2: Run the tests to verify that the new ones fail**

Run:
```bash
mvn -o test -Dtest='LoanPoliciesAutomaticRenewalApiTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: the 11 new test cases fail with 201 instead of 422, matcher `matchesUnprocessableEntity`. The 9 tests from Task 1 still pass.

- [ ] **Step 3: Call the validator from `LoanPoliciesAPI.validate(...)`**

In `src/main/java/org/folio/rest/impl/LoanPoliciesAPI.java`:

1. Add these imports:
   - `import java.util.Optional;` after `import java.util.Map;`
   - `import org.folio.service.policy.LoanPolicyAutomaticRenewalValidator;` after `import org.folio.rest.tools.utils.ValidationHelper;`

2. After `private static final Class<LoanPolicy> LOAN_POLICY_CLASS = LoanPolicy.class;` add:

```java
  private static final LoanPolicyAutomaticRenewalValidator AUTOMATIC_RENEWAL_VALIDATOR =
    new LoanPolicyAutomaticRenewalValidator();
```

3. At the end of `validate(...)`, replace:

```java
      return;
    }

    runIfValid.run();
  }
}
```

with:

```java
      return;
    }

    Optional<Errors> automaticRenewalErrors = AUTOMATIC_RENEWAL_VALIDATOR.validate(loanPolicy);
    if (automaticRenewalErrors.isPresent()) {
      runIfInvalid.accept(automaticRenewalErrors.get());
      return;
    }

    runIfValid.run();
  }
}
```

- [ ] **Step 4: Run the new and the existing loan policy API tests**

Run:
```bash
mvn -o test -Dtest='LoanPoliciesAutomaticRenewalApiTest,LoanPoliciesApiTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: BUILD SUCCESS, with 20 tests in `LoanPoliciesAutomaticRenewalApiTest` and 20 in `LoanPoliciesApiTest`, no failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/rest/impl/LoanPoliciesAPI.java src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java
git commit -m "CIRCSTORE-680: Validate loan policy automatic renewal settings on POST and PUT"
```

---

### Task 4: Legacy policies, clearing via PUT, and CQL queries

These tests cover behaviour that needs no production code. They are expected to pass at once. If any fails, STOP and report the failure (HTTP status, body, generated SQL from the log) instead of changing production code. The spec says such a fix is decided at that point.

**Files:**
- Test: `src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java`

**Interfaces:**
- Consumes: the helpers from Task 1 (`create`, `put`, `get`, `getFromCollection`, `queryIds`, `enabledPolicy`, `renewalsPolicy`, the constants); the `contains` import is already present.

- [ ] **Step 1: Add the tests**

Add these methods to `LoanPoliciesAutomaticRenewalApiTest`, after the tests from Task 3:

```java
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
```

- [ ] **Step 2: Run the tests**

Run:
```bash
mvn -o test -Dtest='LoanPoliciesAutomaticRenewalApiTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `Tests run: 24, Failures: 0, Errors: 0` and BUILD SUCCESS. If any of the 4 new tests fails, stop and report as described at the top of this task.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java
git commit -m "CIRCSTORE-680: Test legacy policies, clearing via PUT and CQL for automatic renewal"
```

---

### Task 5: Interface version, examples and release notes

**Files:**
- Modify: `ramls/loan-policy-storage.raml:3`
- Modify: `descriptors/ModuleDescriptor-template.json:141`
- Modify: `ramls/examples/loan-policy.json:25-35`
- Modify: `ramls/examples/loan-policies.json:21-30`
- Modify: `NEWS.md:1-3`

**Interfaces:**
- Consumes: the schema fields from Task 1.
- Produces: the published `loan-policy-storage` 2.5 interface.

- [ ] **Step 1: Bump the RAML version**

In `ramls/loan-policy-storage.raml`, replace `version: v2.4` with `version: v2.5`.

- [ ] **Step 2: Bump the ModuleDescriptor interface version**

In `descriptors/ModuleDescriptor-template.json`, replace:

```json
      "id": "loan-policy-storage",
      "version": "2.4",
```

with:

```json
      "id": "loan-policy-storage",
      "version": "2.5",
```

- [ ] **Step 3: Update the single-policy example**

In `ramls/examples/loan-policy.json`, replace:

```json
    "alternateFixedDueDateScheduleId": ""
  },
```

with:

```json
    "alternateFixedDueDateScheduleId": "",
    "automaticRenewal": true,
    "firstAutomaticRenewalAttemptDaysBeforeDueDate": 3,
    "automaticRenewalRetryPeriodInDays": 1
  },
```

- [ ] **Step 4: Update the collection example**

In `ramls/examples/loan-policies.json`, replace the first policy's period block, from `"period": {` through the line before `"renewalsPolicy"`'s closing brace:

```json
        "differentPeriod": true,
        "period": {
          "duration": 30,
          "intervalId": "Days"
        }
      }
    },
```

with:

```json
        "differentPeriod": true,
        "period": {
          "duration": 30,
          "intervalId": "Days"
        },
        "automaticRenewal": true,
        "firstAutomaticRenewalAttemptDaysBeforeDueDate": 3,
        "automaticRenewalRetryPeriodInDays": 1
      }
    },
```

Leave the second ("A minimal loan policy") unchanged, as the legacy shape.

- [ ] **Step 5: Add the release note**

In `NEWS.md`, replace:

```markdown
## 17.6.0 2026-XX-XX
* PubSub deprecation: add Kafka support (CIRCSTORE-417)
* PubSub deprecation: Implement Kafka LOG_RECORD event publishing, remove PubSub infrastructure (CIRCSTORE-419)
```

with:

```markdown
## 17.6.0 2026-XX-XX
* PubSub deprecation: add Kafka support (CIRCSTORE-417)
* PubSub deprecation: Implement Kafka LOG_RECORD event publishing, remove PubSub infrastructure (CIRCSTORE-419)
* Add automatic renewal settings to loan policy, bump `loan-policy-storage` to 2.5 (CIRCSTORE-680)
```

- [ ] **Step 6: Validate the JSON files**

Run:
```bash
for f in ramls/examples/loan-policy.json ramls/examples/loan-policies.json ramls/loan-policy.json descriptors/ModuleDescriptor-template.json; do python3 -m json.tool "$f" > /dev/null && echo "OK $f"; done
```

Expected: four `OK` lines.

- [ ] **Step 7: Run the full build with all tests**

Run (takes several minutes; use a 30-minute timeout):
```bash
mvn -o clean install
```

Expected: BUILD SUCCESS. Surefire reports no failures for `StorageTestSuite` (which now includes `LoanPoliciesAutomaticRenewalApiTest`), `TenantRefApiTests` and the unit tests (including `LoanPolicyAutomaticRenewalValidatorTest`). `folio-module-descriptor-validator` passes.

- [ ] **Step 8: Inspect the published interface (Scenario 14)**

Run:
```bash
grep -n '"loan-policy-storage"' -A1 target/ModuleDescriptor.json
grep -n "^version" ramls/loan-policy-storage.raml
grep -c "automaticRenewal" ramls/examples/loan-policy.json ramls/examples/loan-policies.json ramls/loan-policy.json
```

Expected:
- `"version": "2.5"` follows `"loan-policy-storage"`.
- `version: v2.5`.
- Non-zero counts for all three files.

If `target/ModuleDescriptor.json` doesn't exist, run `ls target/*.json` and check the generated descriptor's name.

- [ ] **Step 9: Commit**

```bash
git add ramls/loan-policy-storage.raml descriptors/ModuleDescriptor-template.json ramls/examples/loan-policy.json ramls/examples/loan-policies.json NEWS.md
git commit -m "CIRCSTORE-680: Bump loan-policy-storage to 2.5, update examples and NEWS"
```

---

## Spec Coverage

| Ticket requirement / scenario | Task |
|---|---|
| R1-R3 fields, R11 range | Task 1 (schema + `outOfRangeTimingValuesAreRejected`) |
| R4, R18, Scenario 1 legacy | Task 1 (no default), Task 4 `legacyPolicyStaysWithoutAutomaticRenewalFields` |
| R5-R9, R12, Scenarios 6, 8, 12 | Tasks 2-3 |
| R10, Scenarios 4, 5 | Task 1 `boundaryTimingValuesAreAccepted`, Task 2 zero-timing unit test |
| R13, Scenarios 2, 3 | Task 1 `enabledConfigurationIsPersistedAndReturnedUnchanged` |
| R14, Scenarios 10, 11 | Task 4 |
| R15, Scenario 13 | Task 4 `canQueryByAutomaticRenewal` |
| R16, R17, Scenario 14 | Task 5 |
| Scenario 9, coercion parts of R11 / Scenario 7 | Not implemented by decision; see the spec, "Known deviations from the ticket" |
