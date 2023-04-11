package org.folio.service.migration;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestMigrationContext {
  private final JsonObject oldRequest;
  private JsonObject newRequest;
  private final String requestId;

  public RequestMigrationContext(JsonObject request) {
    this.oldRequest = request;
    this.requestId = request.getString("id");
  }
}