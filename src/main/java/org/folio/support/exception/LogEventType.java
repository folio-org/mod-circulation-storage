package org.folio.support.exception;

public enum LogEventType {
  REQUEST_EXPIRED("REQUEST_EXPIRED_EVENT");

  private final String value;

  LogEventType(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }
}
