package org.folio.service.migration;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestSearchMigrationContext extends RequestMigrationContext {
  private final String pickupServicePointId;
  private String pickupServicePointName;
  private final String itemId;
  private RequestSearchFieldsMigrationService.CallNumberComponents callNumberComponents;
  private String shelvingOrder;

  public RequestSearchMigrationContext(JsonObject request) {
    super(request);
    this.pickupServicePointId = request.getString("pickupServicePointId");
    this.itemId = request.getString("itemId");
  }
}