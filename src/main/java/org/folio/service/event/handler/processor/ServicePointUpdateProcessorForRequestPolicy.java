package org.folio.service.event.handler.processor;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.folio.service.event.InventoryEventType.INVENTORY_SERVICE_POINT_UPDATED;
import static org.folio.service.event.handler.processor.util.AllowedServicePointsUtil.buildContainsServicePointCriterion;
import static org.folio.service.event.handler.processor.util.AllowedServicePointsUtil.removeServicePointFromRequestPolicy;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestPolicyRepository;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.json.JsonObject;

public class ServicePointUpdateProcessorForRequestPolicy extends UpdateEventProcessor<RequestPolicy> {
  private static final Logger log = LogManager.getLogger(ServicePointUpdateProcessorForRequestPolicy.class);
  private static final String SERVICE_POINT_PICKUP_LOCATION = "pickupLocation";

  public ServicePointUpdateProcessorForRequestPolicy(
    RequestPolicyRepository requestPolicyRepository) {

    super(INVENTORY_SERVICE_POINT_UPDATED, requestPolicyRepository);
  }

  @Override
  protected List<Change<RequestPolicy>> collectRelevantChanges(JsonObject payload) {
    JsonObject oldObject = payload.getJsonObject("old");
    JsonObject newObject = payload.getJsonObject("new");
    List<Change<RequestPolicy>> changes = new ArrayList<>();
    String updatedServicePointId = oldObject.getString("id");

    Boolean isOldServicePointPickupLocation = oldObject.getBoolean(SERVICE_POINT_PICKUP_LOCATION);
    Boolean isNewServicePointPickupLocation = newObject.getBoolean(SERVICE_POINT_PICKUP_LOCATION);
    if (isOldServicePointPickupLocation == TRUE && isNewServicePointPickupLocation == FALSE) {
      log.info("collectRelevantChanges:: pickupLocation was changed from true to false");
      changes.add(new Change<>(requestPolicy -> removeServicePointFromRequestPolicy(requestPolicy,
        updatedServicePointId)));
    }

    return changes;
  }

  @Override
  protected Criterion criterionForObjectsToBeUpdated(String oldObjectId) {
    log.info("criterionForObjectsToBeUpdated:: oldObjectId: {}", oldObjectId);

    return buildContainsServicePointCriterion(oldObjectId);
  }
}
