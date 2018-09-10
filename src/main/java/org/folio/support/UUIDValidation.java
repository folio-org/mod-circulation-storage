package org.folio.support;

import java.util.regex.Pattern;

public class UUIDValidation {
  private static final String UUID_PATTERN = "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$";

  private UUIDValidation() { }

  public static Boolean isValidUUID(String prospectiveUuid) {
    return Pattern.matches(UUID_PATTERN, prospectiveUuid);
  }
}
