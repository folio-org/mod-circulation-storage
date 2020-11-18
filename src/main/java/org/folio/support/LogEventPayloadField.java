package org.folio.support;

public enum LogEventPayloadField {

  PAYLOAD("payload"),
  LOG_EVENT_TYPE("logEventType"),
  REQUESTS("requests"),
  ORIGINAL("original"),
  UPDATED("updated");

  private final String value;

  LogEventPayloadField(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }
}
