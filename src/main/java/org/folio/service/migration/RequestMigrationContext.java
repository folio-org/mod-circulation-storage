package org.folio.service.migration;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class RequestMigrationContext {
  private final JsonObject oldRequest;
  private JsonObject newRequest;
  @ToString.Include
  private final String requestId;

  public RequestMigrationContext(JsonObject request) {
    this.oldRequest = request;
    this.requestId = request.getString("id");
  }
}