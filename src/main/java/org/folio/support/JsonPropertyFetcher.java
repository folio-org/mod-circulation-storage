package org.folio.support;

import java.util.Optional;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class JsonPropertyFetcher {
  private JsonPropertyFetcher() { }

  public static UUID getUUIDProperty(JsonObject representation, String propertyName) {
    return Optional.ofNullable(representation)
      .map(json -> json.getString(propertyName))
      .map(UUID::fromString)
      .orElse(null);
  }

  public static boolean getBooleanProperty(JsonObject representation, String propertyName) {
    return Optional.ofNullable(representation)
      .map(json -> json.getBoolean(propertyName))
      .orElse(false);
  }
}
