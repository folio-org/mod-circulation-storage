package org.folio.service.event.handler.processor;

import static org.folio.service.event.InventoryEventType.INVENTORY_SERVICE_POINT_DELETED;
import static org.folio.service.event.handler.processor.util.AllowedServicePointsUtil.removeServicePointFromRequestPolicy;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestPolicyRepository;
import org.folio.rest.jaxrs.model.RequestPolicy;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ServicePointDeleteProcessorForRequestPolicy
  extends DeleteEventProcessor<RequestPolicy> {

  private static final Logger log = LogManager.getLogger();

  public ServicePointDeleteProcessorForRequestPolicy(
    RequestPolicyRepository requestPolicyRepository) {

    super(INVENTORY_SERVICE_POINT_DELETED, requestPolicyRepository);
  }

  @Override
  protected List<Change<RequestPolicy>> collectRelevantChanges(JsonObject payload) {
    log.debug("collectRelevantChanges:: payload: {}", payload);

    JsonObject oldObject = payload.getJsonObject("old");

    List<Change<RequestPolicy>> changes = new ArrayList<>();
    String deletedServicePointId = oldObject.getString("id");

    changes.add(new Change<>(requestPolicy -> {
      removeServicePointFromRequestPolicy(requestPolicy, deletedServicePointId);
    }));

    return changes;
  }

  @Override
  protected Future<List<RequestPolicy>> findObjectsToBeUpdated(String oldObjectId) {
    return ((RequestPolicyRepository) repository).findByServicePointId(oldObjectId);
  }
}
