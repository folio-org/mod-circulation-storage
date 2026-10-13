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
 * are enforced by Bean Validation on the model generated from the JSON schema.
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
