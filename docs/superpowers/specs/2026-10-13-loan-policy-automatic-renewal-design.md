# CIRCSTORE-680: Persist automatic-renewal configuration in Loan Policy

Jira: https://folio-org.atlassian.net/browse/CIRCSTORE-680

## Goal

Extend the Loan Policy storage schema with an optional automatic-renewal
configuration under `renewalsPolicy`, enforce its cross-field rules on POST and
PUT, and support CQL filtering by `renewalsPolicy.automaticRenewal`. Existing
Loan Policies stay valid without migration.

## Decisions

- **Approach A:** cross-field rules live in a dedicated, synchronous validator
  class called from `LoanPoliciesAPI.validate(...)`.
- **Type coercion:** RMB's shared Jackson mapper is used unchanged (decided
  with the user). See "Known deviations from the ticket".
- **No DB index** on `renewalsPolicy.automaticRenewal`. `loan_policy` is a small
  configuration table; unindexed CQL on JSONB is sufficient.
- **Interface version:** `loan-policy-storage` 2.4 -> 2.5 (additive minor bump,
  same as the CIRCSTORE-578 precedent).

## Schema changes

File: `ramls/loan-policy.json`, inside `renewalsPolicy.properties`:

| Field | Type | Constraints | Description |
|---|---|---|---|
| `automaticRenewal` | boolean | none, no `default` | Whether automatic renewal is enabled. Absent means disabled. |
| `firstAutomaticRenewalAttemptDaysBeforeDueDate` | integer | `minimum: 0`, `maximum: 100` | Days before the due date of the first automatic renewal attempt; `0` means on the due date. |
| `automaticRenewalRetryPeriodInDays` | integer | `minimum: 0`, `maximum: 100` | Retry period in days for automatic renewal attempts. |

- No `default` on `automaticRenewal`: a default would make jsonschema2pojo
  initialise the field to `false`, which adds the field to every newly saved
  record and breaks requirement 4 / Scenario 1.
- `additionalProperties` of `renewalsPolicy` is left as is (not tightened) to
  avoid a breaking change for existing clients.
- Generated model `RenewalsPolicy` gains `Boolean automaticRenewal`,
  `Integer firstAutomaticRenewalAttemptDaysBeforeDueDate` and
  `Integer automaticRenewalRetryPeriodInDays`. The integer fields get
  `@DecimalMin("0") @DecimalMax("100")`.
- Range violations are rejected by RMB Bean Validation before the API code
  runs: HTTP 422, standard `Errors` body, parameter key
  `renewalsPolicy.<field>`, messages `must be greater than or equal to 0` /
  `must be less than or equal to 100`.

## Cross-field validation

New class `org.folio.service.policy.LoanPolicyAutomaticRenewalValidator`
(stateless, synchronous):

```java
public Optional<Errors> validate(LoanPolicy loanPolicy)
```

Let `rp = loanPolicy.getRenewalsPolicy()`. If `rp` is null, the policy is
valid. Otherwise, rules are checked in this order and the first failure is
returned (same first-failure semantics as the existing rules):

| # | Condition | Error parameter key | Value | Message |
|---|---|---|---|---|
| 1 | `renewable` is not `true` and any of the three fields is non-null | first non-null field in order `automaticRenewal`, `firstAutomaticRenewalAttemptDaysBeforeDueDate`, `automaticRenewalRetryPeriodInDays`, prefixed `renewalsPolicy.` | that field's value | `Automatic renewal settings are not allowed when renewable is false` |
| 2 | `automaticRenewal` is null and a timing field is non-null | `renewalsPolicy.<timing field>` (first non-null) | its value | `<field> is not allowed when automaticRenewal is not set` |
| 3 | `automaticRenewal` is `false` and a timing field is non-null | `renewalsPolicy.<timing field>` (first non-null) | its value | `<field> is not allowed when automaticRenewal is false` |
| 4 | `automaticRenewal` is `true` and a timing field is null | `renewalsPolicy.<timing field>` (first null, `firstAutomaticRenewalAttemptDaysBeforeDueDate` checked first) | `null` | `<field> is required when automaticRenewal is true` |

- Errors are built with
  `ValidationHelper.createValidationErrorMessage(key, value, message)`, the
  same helper the existing rules use.
- `renewable` is required by the schema (`@NotNull`), so in rule 1 "not `true`"
  in practice means `false`.

Integration in `LoanPoliciesAPI`:

- A `private static final LoanPolicyAutomaticRenewalValidator` field.
- At the end of `validate(...)`, after the four existing rules and before
  `runIfValid.run()`: if the validator returns errors, call
  `runIfInvalid.accept(errors)` and return.
- POST and PUT already share `validate(...)` and `respond422(...)`, so both get
  identical behaviour (requirement 12).

## Persistence, PUT and querying

- **Persistence:** no change. Data is stored as JSONB through the existing
  `PgUtil.post` / `MyPgUtil.putUpsert204` paths. Models use
  `@JsonInclude(NON_NULL)`, so absent fields stay absent in storage and
  responses.
- **Replacement PUT (requirement 14):** `putUpsert204` replaces the whole
  JSONB document, so a PUT with `automaticRenewal: false` and no timing
  fields, or with `renewable: false` and no automatic-renewal fields, removes
  the previously stored values. No code change needed; this is covered by
  tests.
- **CQL (requirement 15):** without an index, CQL2PgJSON compares the JSONB
  text value (`jsonb->'renewalsPolicy'->>'automaticRenewal'`). An absent field
  is SQL NULL and matches neither `==true` nor `==false`. Covered by tests.
- **No migration:** reference data (`reference-data/loan-policy-storage/...`)
  and existing records are not touched (requirement 18).

## Interface, docs and release notes

- `ramls/loan-policy-storage.raml`: `version: v2.4` -> `version: v2.5`.
- `descriptors/ModuleDescriptor-template.json`: `loan-policy-storage` provides
  version `2.4` -> `2.5`.
- `ramls/examples/loan-policy.json`: add an enabled configuration to
  `renewalsPolicy` (`automaticRenewal: true`,
  `firstAutomaticRenewalAttemptDaysBeforeDueDate: 3`,
  `automaticRenewalRetryPeriodInDays: 1`).
- `ramls/examples/loan-policies.json`: add the same configuration to one of the
  two policies; leave the other without it (legacy shape).
- `NEWS.md`, under `## 17.6.0 2026-XX-XX`:
  `* Add automatic renewal settings to loan policy, bump loan-policy-storage to 2.5 (CIRCSTORE-680)`
- Models are regenerated by the normal Maven build (`domain-models-maven-plugin`).

## Testing

**Unit tests**, `src/test/java/org/folio/service/policy/LoanPolicyAutomaticRenewalValidatorTest.java`
(JUnit 5, no infrastructure, picked up by surefire via `org/folio/**`):

- Valid: `renewalsPolicy` null; no automatic-renewal fields; enabled with both
  timings, including `0/0` and `100/100`; `automaticRenewal: false` with no
  timings.
- Rule 1: `renewable: false` with each field individually present.
- Rule 2: toggle absent with each timing field.
- Rule 3: toggle `false` with each timing field.
- Rule 4: toggle `true` missing each timing field, and missing both.
- Each failing case asserts the parameter key, value and message.

**API tests**, new class
`src/test/java/org/folio/rest/api/LoanPoliciesAutomaticRenewalApiTest.java`
(extends `ApiTests`, registered in `StorageTestSuite` `@SelectClasses`). It
reuses `LoanPoliciesApiTest`'s style (JSON built via `LoanPolicyRequestBuilder`
and then edited with `getJsonObject("renewalsPolicy").put(...)`):

| Scenario | Test |
|---|---|
| 1 | Legacy policy: POST and PUT without the fields; item GET and collection GET contain no automatic-renewal fields |
| 2, 3 | Enabled policy: POST response, item GET and collection GET return the values unchanged; PUT with changed values, then GET returns them |
| 4, 5 | `firstAutomaticRenewalAttemptDaysBeforeDueDate: 0` and `automaticRenewalRetryPeriodInDays: 0` are accepted and returned |
| 6 | Enabled with each timing field missing or `null` -> 422 on POST and PUT; GET after a failed PUT shows the stored state unchanged |
| 7 | `-1` and `101` for each timing field -> 422 on POST and PUT, with Bean Validation key/message |
| 8 | `renewable: false` with automatic-renewal fields -> 422 on POST and PUT |
| 10 | Enabled policy, PUT with `automaticRenewal: false` and no timings -> 204; item and collection GET show `false` and no timing fields |
| 11 | Enabled policy, PUT with `renewable: false` and no automatic-renewal fields -> 204; item and collection GET show none of the fields |
| 12 | Timing fields with toggle absent or `false` -> 422 on POST and PUT |
| 13 | Three policies (enabled, disabled, absent); `renewalsPolicy.automaticRenewal==true` returns only enabled; `==false` returns only disabled |

A "POST and PUT" test runs the same invalid payload against both endpoints;
for PUT it first creates a valid policy and then checks it is unchanged.

Scenario 9 (non-boolean toggle) and the type-coercion parts of Scenario 7 are
not tested as rejections; see the next section.

Scenario 14 (published interface) is checked by inspecting the RAML,
ModuleDescriptor and examples after `mvn clean install`.

## Known deviations from the ticket

The user decided to keep RMB's default Jackson deserialization. RMB 36.0.0
(`RestRouting.parseParams1`) uses a plain `ObjectMapper` with default coercion,
and RMB has no runtime JSON-schema validation. Behaviour verified with
jackson-databind 2.18.6:

| Input | Result | Requirement affected |
|---|---|---|
| `"5"` for a timing field | accepted as `5` | 11 (strings) |
| `1.5` for a timing field | accepted, truncated to `1` | 11 (non-integer) |
| `""` / `" "` for a timing field | treated as absent | 11 (blank); rejected by rule 4 when `automaticRenewal: true`, otherwise accepted and not stored |
| `null` for a timing field | treated as absent | 11 (null); rejected when `automaticRenewal: true` by rule 4 |
| `"true"`, `1`, `0` for `automaticRenewal` | coerced to `true` / `true` / `false` | 9 |
| `""` / `null` for `automaticRenewal` | treated as absent | 9 |
| `"yes"`, `"abc"` | Jackson `InvalidFormatException` -> HTTP 400 (text) | 9, 11: rejected, but with 400 rather than the 422 `Errors` body |

These deviations should be recorded on the Jira ticket. Strict rejection would
need custom Jackson deserializers registered on RMB's shared mapper, which can
be done as a follow-up if required.

## Out of scope

UI form changes, scheduling and processing of automatic renewals, eligibility
evaluation, migration or backfill, and a DB index on the new field.
