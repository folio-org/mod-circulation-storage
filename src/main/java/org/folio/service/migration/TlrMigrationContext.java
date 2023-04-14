package org.folio.service.migration;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TlrMigrationContext extends RequestMigrationContext {
  private final String itemId;
  private String holdingsRecordId;
  private String instanceId;

  public TlrMigrationContext(JsonObject request) {
    super(request);
    this.itemId = request.getString("itemId");
  }
}