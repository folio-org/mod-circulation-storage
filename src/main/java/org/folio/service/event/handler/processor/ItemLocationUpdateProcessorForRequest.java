package org.folio.service.event.handler.processor;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.folio.service.event.InventoryEventType.INVENTORY_ITEM_UPDATED;
import static org.folio.service.event.handler.processor.ItemUpdateProcessorForRequest.ITEM_EFFECTIVE_LOCATION_ID;

public class ItemLocationUpdateProcessorForRequest extends UpdateEventProcessor<Request> {
  private static final Logger log = LogManager.getLogger(ItemLocationUpdateProcessorForRequest.class);

  private static final String LOCATION_NAME_KEY = "name";


  public ItemLocationUpdateProcessorForRequest(RequestRepository repository) {
    super(INVENTORY_ITEM_UPDATED, repository);
  }

  @Override
  protected Future<List<Change<Request>>> collectRelevantChanges(JsonObject payload) {
    JsonObject oldObject = payload.getJsonObject("old");
    JsonObject newObject = payload.getJsonObject("new");

    List<Change<Request>> changes = new ArrayList<>();

    // compare shelving order
    String oldLocationName = oldObject.getString(LOCATION_NAME_KEY);
    String newLocationName = newObject.getString(LOCATION_NAME_KEY);
    if (notEqual(oldLocationName, newLocationName)) {
      log.info("ItemLocationUpdateProcessorForRequest :: collectRelevantChanges:: changing item.itemEffectiveLocationId from {} to {}",
              oldLocationName, newLocationName);
      changes.add(new Change<>(request -> request.getItem().setItemEffectiveLocationName(newLocationName)));
    }

    return Future.succeededFuture(changes);
  }

  @Override
  protected Criterion criterionForObjectsToBeUpdated(String oldObjectId) {
    log.info("ItemLocationUpdateProcessorForRequest :: criterionForObjectsToBeUpdated:: oldObjectId: {}",
            oldObjectId);
    return new Criterion(
      new Criteria()
        .addField("'item'")
        .addField(String.format("'%s'", ITEM_EFFECTIVE_LOCATION_ID))
        .setOperation("=")
        .setVal(oldObjectId));
  }
}
