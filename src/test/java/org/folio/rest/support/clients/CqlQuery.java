package org.folio.rest.support.clients;

import java.util.stream.Stream;

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
    final Object[] maskedValues = Stream.of(values)
      .map(CqlQuery::maskCqlValue).toArray();

    return new CqlQuery(String.format(template, maskedValues));
  }

  public final String asString() {
    return query;
  }

  private static Object maskCqlValue(Object value) {
    if (value == null) {
      return null;
    }

    return value.toString()
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("*", "\\*")
      .replace("?", "\\?")
      .replace("^", "\\^");
  }
}
