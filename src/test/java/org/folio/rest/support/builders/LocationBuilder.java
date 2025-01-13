package org.folio.rest.support.builders;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

import java.util.UUID;

@With
@AllArgsConstructor
public class LocationBuilder extends JsonBuilder implements Builder {
  private final String id;
  private final String name;
  private final String code;
  private final String primaryServicePoint;

  public LocationBuilder(String name) {
    this(UUID.randomUUID().toString(), name, null, null);
  }

  @Override
  public JsonObject create() {
    JsonObject location = new JsonObject();
    put(location, "id", this.id);
    put(location, "name", this.name);
    put(location, "code", this.code);
    put(location, "primaryServicePoint", this.primaryServicePoint);
    return location;
  }
}
