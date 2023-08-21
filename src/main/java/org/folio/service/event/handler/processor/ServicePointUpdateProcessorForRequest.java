package org.folio.service.event.handler.processor;

import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.folio.service.event.InventoryEventType.INVENTORY_SERVICE_POINT_UPDATED;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ServicePointUpdateProcessorForRequest extends UpdateEventProcessor<Request> {
  private static final Logger log = LogManager.getLogger(ServicePointUpdateProcessorForRequest.class);
  private static final String SERVICE_POINT_NAME_KEY = "name";

  public ServicePointUpdateProcessorForRequest(RequestRepository requestRepository) {
    super(INVENTORY_SERVICE_POINT_UPDATED, requestRepository);
  }

  @Override
  protected List<Change<Request>> collectRelevantChanges(JsonObject payload) {
    JsonObject oldObject = payload.getJsonObject("old");
    JsonObject newObject = payload.getJsonObject("new");

    List<Change<Request>> changes = new ArrayList<>();

    // compare service point names
    String oldServicePointName = oldObject.getString(SERVICE_POINT_NAME_KEY);
    String newServicePointName = newObject.getString(SERVICE_POINT_NAME_KEY);
    if (notEqual(oldServicePointName, newServicePointName)) {
      log.info("collectRelevantChanges:: changing searchIndex.pickupServicePointName from {} to {}",
        oldServicePointName, newServicePointName);
      changes.add(new Change<>(request -> request.getSearchIndex()
        .setPickupServicePointName(newServicePointName)));
    }

    return changes;
  }

  @Override
  protected Future<List<Request>> applyChanges(List<Change<Request>> changes, JsonObject payload) {
    log.debug("applyChanges:: applying searchIndex.pickupServicePointName changes");

    JsonObject oldObject = payload.getJsonObject("old");

    return findRequestsByPickupServicePointId(oldObject.getString("id"))
      .compose(requests -> applyDbUpdates(requests, changes));
  }

  private Future<List<Request>> findRequestsByPickupServicePointId(String pickupServicePointId) {
    log.info("findRequestsByServicePointId:: fetching requests for pickupServicePointId {}",
      pickupServicePointId);

    return repository.get(new Criterion(
      new Criteria()
        .addField("'pickupServicePointId'")
        .setOperation("=")
        .setVal(pickupServicePointId)));
  }
}
