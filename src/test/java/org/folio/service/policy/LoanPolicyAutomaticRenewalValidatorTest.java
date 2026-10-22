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
