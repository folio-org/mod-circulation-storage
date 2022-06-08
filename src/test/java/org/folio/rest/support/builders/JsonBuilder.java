package org.folio.rest.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class JsonBuilder {
  protected void put(JsonObject representation, String property, String value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, Integer value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, UUID value) {
    if(value != null) {
      representation.put(property, value.toString());
    }
  }

  protected void put(JsonObject representation, String property, Boolean value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, DateTime value) {
    if(value != null) {
      representation.put(property, value.toString(ISODateTimeFormat.dateTime()));
    }
  }

  protected void put(JsonObject representation, String property, LocalDate value) {
    if(value != null) {
      representation.put(property, formatDateOnly(value));
    }
  }

  protected void put(JsonObject representation, String property, JsonObject value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, List<?> value) {
    if (value != null && !value.isEmpty()) {
      representation.put(property, new JsonArray(value));
    }
  }

  protected void put(
    JsonObject request,
    String property,
    Object check,
    JsonObject value) {

    if(check != null) {
      request.put(property, value);
    }
  }

  String formatDateOnly(LocalDate date) {
    return date.toString(DateTimeFormat.forPattern("yyyy-MM-dd"));
  }
}
