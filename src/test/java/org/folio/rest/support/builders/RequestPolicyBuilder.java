package org.folio.rest.support.builders;

import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.AllowedServicePoints;
import org.folio.rest.jaxrs.model.RequestType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@AllArgsConstructor
@With
public class RequestPolicyBuilder extends JsonBuilder {
  private final String id;
  private final String name;
  private final String description;
  private final List<RequestType> requestTypes;
  private final AllowedServicePoints allowedServicePoints;

  public RequestPolicyBuilder() {
    this(UUID.randomUUID().toString(), "", "", List.of(), new AllowedServicePoints());
  }

  public JsonObject create() {
    JsonObject requestPolicy = new JsonObject();

    put(requestPolicy, "id", this.id);
    put(requestPolicy, "name", this.name);
    put(requestPolicy, "description", this.description);
    put(requestPolicy, "requestTypes", new JsonArray(this.requestTypes));

    if (allowedServicePoints != null) {
      JsonObject allowedServicePointsObject = new JsonObject();
      if (allowedServicePoints.getHold() != null && !allowedServicePoints.getHold().isEmpty()) {
        put(allowedServicePointsObject, "Hold", new JsonArray(
          List.copyOf(allowedServicePoints.getHold())));
      }
      if (allowedServicePoints.getPage() != null && !allowedServicePoints.getPage().isEmpty()) {
        put(allowedServicePointsObject, "Page", new JsonArray(
          List.copyOf(allowedServicePoints.getPage())));
      }
      if (allowedServicePoints.getRecall() != null && !allowedServicePoints.getRecall().isEmpty()) {
        put(allowedServicePointsObject, "Recall", new JsonArray(
          List.copyOf(allowedServicePoints.getRecall())));
      }

      put(requestPolicy, "allowedServicePoints", allowedServicePointsObject);
    }

    return requestPolicy;
  }
}
