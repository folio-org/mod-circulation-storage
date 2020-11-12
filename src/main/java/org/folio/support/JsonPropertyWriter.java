package org.folio.support;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonObject;

public class JsonPropertyWriter {

  private JsonPropertyWriter() {
  }

  public static void write(JsonObject to, String propertyName, String value) {

    if (StringUtils.isNotBlank(value)) {
      to.put(propertyName, value);
    }
  }
}
