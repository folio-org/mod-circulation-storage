package org.folio.support;

import java.util.Arrays;
import java.util.Optional;

/**
 * Values of the user record's {@code loanAnonymizationPreference} in mod-users.
 */
public enum LoanAnonymizationPreference {
  LIBRARY_POLICY("libraryPolicy"),
  AS_EARLY_AS_POSSIBLE("asEarlyAsPossible"),
  NEVER("never");

  private final String value;

  LoanAnonymizationPreference(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean deviatesFromLibraryPolicy() {
    return this != LIBRARY_POLICY;
  }

  public static Optional<LoanAnonymizationPreference> fromValue(String value) {
    return Arrays.stream(values())
      .filter(preference -> preference.value.equals(value))
      .findFirst();
  }
}
