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
import static org.folio.service.event.InventoryEventType.INVENTORY_SERVICE_POINT_UPDATED;
import static org.folio.service.event.handler.processor.ItemUpdateProcessorForRequest.RETRIEVAL_SERVICE_POINT_ID;

public class ItemRetrievalServicePointUpdateProcessorForRequest extends UpdateEventProcessor<Request> {
  private static final Logger log = LogManager.getLogger(ItemRetrievalServicePointUpdateProcessorForRequest.class);
  private static final String SERVICE_POINT_NAME_KEY = "name";

  public ItemRetrievalServicePointUpdateProcessorForRequest(RequestRepository requestRepository) {
    super(INVENTORY_SERVICE_POINT_UPDATED, requestRepository);
  }

  @Override
  protected Future<List<Change<Request>>> collectRelevantChanges(JsonObject payload) {
    JsonObject newObject = payload.getJsonObject("new");
    JsonObject oldObject = payload.getJsonObject("old");
    List<Change<Request>> changes = new ArrayList<>();

    // compare service point names
    String newServicePointName = newObject.getString(SERVICE_POINT_NAME_KEY);
    String oldServicePointName = oldObject.getString(SERVICE_POINT_NAME_KEY);
    if (notEqual(oldServicePointName, newServicePointName)) {
      log.info("ItemRetrievalServicePointUpdateProcessorForRequest :: collectRelevantChanges:: changing item.retrievalServicePointName from {} to {}",
              oldServicePointName, newServicePointName);
      changes.add(new Change<>(request -> request.getItem().setRetrievalServicePointName(newServicePointName)));
    }
    return Future.succeededFuture(changes);
  }

  @Override
  protected Criterion criterionForObjectsToBeUpdated(String oldObjectId) {
    log.info("ItemRetrievalServicePointUpdateProcessorForRequest :: criterionForObjectsToBeUpdated:: oldObjectId: {}",
            oldObjectId);
    return new Criterion(
      new Criteria()
        .addField("'item'")
        .addField(String.format("'%s'", RETRIEVAL_SERVICE_POINT_ID))
        .setOperation("=")
        .setVal(oldObjectId));
  }
}
