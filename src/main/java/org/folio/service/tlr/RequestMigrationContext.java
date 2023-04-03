package org.folio.service.tlr;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class RequestMigrationContext {
  private final JsonObject oldRequest;
  private JsonObject newRequest;
  private final String requestId;
  private final String itemId;
  private String holdingsRecordId;
  private String instanceId;

  private static final String ID_KEY = "id";
  private static final String ITEM_ID_KEY = "itemId";

  public static RequestMigrationContext from(JsonObject request) {
    return new RequestMigrationContext(request,
      request.getString(ID_KEY), request.getString(ITEM_ID_KEY));
  }

  @Override
  public String toString() {
    return "RequestMigrationContext{" +
      "requestId='" + requestId + '\'' +
      ", itemId='" + itemId + '\'' +
      ", holdingsRecordId='" + holdingsRecordId + '\'' +
      ", instanceId='" + instanceId + '\'' +
      '}';
  }
}