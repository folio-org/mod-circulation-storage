package org.folio.rest.support;

import io.vertx.core.json.JsonObject;

public class IndividualResource {

  private final JsonResponse response;

  public IndividualResource(JsonResponse response) {
    this.response = response;
  }

  public String getId() {
    return response.getJson().getString("id");
  }

  public JsonObject copyJson() {
    return response.getJson().copy();
  }
}
