package org.folio.rest.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
public class ServicePointBuilder extends JsonBuilder implements Builder {
  private final String id;
  private final String name;
  private final String code;
  private final String discoveryDisplayName;
  private final String description;
  private final Integer shelvingLagTime;
  private final Boolean pickupLocation;
  private final JsonObject holdShelfExpiryPeriod;
  private final String holdShelfClosedLibraryDateManagement;

  public ServicePointBuilder(String name) {
    this(UUID.randomUUID().toString(), name, null, null, null, null, false, null, null);
  }

  @Override
  public JsonObject create() {
    JsonObject servicePoint = new JsonObject();
    put(servicePoint, "id", this.id);
    put(servicePoint, "name", this.name);
    put(servicePoint, "code", this.code);
    put(servicePoint, "discoveryDisplayName", this.discoveryDisplayName);
    put(servicePoint, "description", this.description);
    put(servicePoint, "shelvingLagTime", this.shelvingLagTime);
    put(servicePoint, "pickupLocation", this.pickupLocation);
    put(servicePoint, "holdShelfExpiryPeriod", this.holdShelfExpiryPeriod);
    put(servicePoint, "holdShelfClosedLibraryDateManagement", this.holdShelfClosedLibraryDateManagement);

    return servicePoint;
  }
}
