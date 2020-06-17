package org.folio.rest.support.clients;

public final class CqlQuery {
  private final String query;

  public CqlQuery(String query) {
    this.query = query;
  }

  public static CqlQuery exactMatch(String propertyName, Object value) {
    return fromTemplate("%s==\"%s\"", propertyName, value);
  }

  public static CqlQuery startsWith(String propertyName, Object value) {
    return fromTemplate("%s=\"%s\"", propertyName, value);
  }

  public static CqlQuery lessThen(String propertyName, Object bound) {
    return fromTemplate("%s<\"%s\"", propertyName, bound);
  }

  public static CqlQuery fromTemplate(String template, Object... values) {
    return new CqlQuery(String.format(template, values));
  }

  public final String asString() {
    return query;
  }
}
