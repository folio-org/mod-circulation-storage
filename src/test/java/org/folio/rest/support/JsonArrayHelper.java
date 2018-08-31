package org.folio.rest.support;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonArrayHelper {
  private JsonArrayHelper() { }

  public static List<JsonObject> toList(
    JsonObject within,
    String arrayPropertyName) {

    return toStream(within, arrayPropertyName)
      .collect(Collectors.toList());
  }

  private static Stream<JsonObject> toStream(
    JsonObject within,
    String arrayPropertyName) {

    if(within == null || !within.containsKey(arrayPropertyName)) {
      return Stream.empty();
    }

    return toStream(within.getJsonArray(arrayPropertyName));
  }

  public static List<JsonObject> toList(JsonArray array) {
    return toStream(array)
      .collect(Collectors.toList());
  }

  private static Stream<JsonObject> toStream(JsonArray array) {
    return array
      .stream()
      .map(castToJsonObject())
      .filter(Objects::nonNull);
  }

  private static Function<Object, JsonObject> castToJsonObject() {
    return record -> {
      if(record instanceof JsonObject) {
        return (JsonObject)record;
      }
      else {
        return null;
      }
    };
  }
}
