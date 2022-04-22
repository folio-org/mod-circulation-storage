package org.folio.support;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class JsonPropertyFetcher {
  private JsonPropertyFetcher() { }

  public static UUID getUUIDProperty(JsonObject representation, String propertyName) {
    if (representation != null && representation.containsKey(propertyName)
      && representation.getString(propertyName) != null) {

      return UUID.fromString(representation.getString(propertyName));
    } else {
      return null;
    }
  }

  public static boolean getBooleanProperty(JsonObject representation, String propertyName) {
    if (representation != null) {
      return representation.getBoolean(propertyName, false);
    } else {
      return false;
    }
  }
}
