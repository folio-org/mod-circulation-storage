package org.folio.service.event.handler.processor;

import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.folio.service.event.InventoryEventType.INVENTORY_ITEM_UPDATED;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.CallNumberComponents;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ItemUpdateProcessorForRequest extends UpdateEventProcessor<Request> {

  private static final Logger log = LogManager.getLogger(ItemUpdateProcessorForRequest.class);
  private static final String EFFECTIVE_SHELVING_ORDER_KEY = "effectiveShelvingOrder";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY = "effectiveCallNumberComponents";
  private static final String CALL_NUMBER_KEY = "callNumber";
  private static final String CALL_NUMBER_PREFIX_KEY = "prefix";
  private static final String CALL_NUMBER_SUFFIX_KEY = "suffix";

  public ItemUpdateProcessorForRequest(RequestRepository repository) {
    super(INVENTORY_ITEM_UPDATED, repository);
  }

  protected List<Change<Request>> collectRelevantChanges(JsonObject payload) {
    JsonObject oldObject = payload.getJsonObject("old");
    JsonObject newObject = payload.getJsonObject("new");

    List<Change<Request>> changes = new ArrayList<>();

    // compare call number components
    JsonObject oldCallNumberComponents = extractCallNumberComponents(oldObject);
    JsonObject newCallNumberComponents = extractCallNumberComponents(newObject);
    if (notEqual(oldCallNumberComponents, newCallNumberComponents)) {
      changes.add(new Change<>(request -> request.getSearchIndex()
        .setCallNumberComponents(newCallNumberComponents.mapTo(CallNumberComponents.class))));
    }

    // compare shelving order
    String oldShelvingOrder = oldObject.getString(EFFECTIVE_SHELVING_ORDER_KEY);
    String newShelvingOrder = newObject.getString(EFFECTIVE_SHELVING_ORDER_KEY);
    if (notEqual(oldShelvingOrder, newShelvingOrder)) {
      changes.add(new Change<>(request -> request.getSearchIndex().setShelvingOrder(newShelvingOrder)));
    }

    return changes;
  }

  @Override
  protected Future<List<Request>> applyChanges(List<Change<Request>> changes, JsonObject payload) {
    log.debug("applyChanges:: applying item-related changes");

    JsonObject oldObject = payload.getJsonObject("old");

    return findRequestsForItem(oldObject.getString("id"))
      .compose(requests -> applyDbUpdates(requests, changes));
  }

  private Future<List<Request>> findRequestsForItem(String itemId) {
    log.info("findRequestsByItemId:: fetching requests for item {}", itemId);

    return repository.get(new Criterion(
      new Criteria()
        .addField("'itemId'")
        .setOperation("=")
        .setVal(itemId)));
  }

  private static JsonObject extractCallNumberComponents(JsonObject itemJson) {
    JsonObject itemCallNumberComponents = itemJson.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY);
    JsonObject callNumberComponents = new JsonObject();

    if (itemCallNumberComponents != null) {
      // extract only properties used in request search index
      callNumberComponents
        .put(CALL_NUMBER_KEY, itemCallNumberComponents.getString(CALL_NUMBER_KEY))
        .put(CALL_NUMBER_PREFIX_KEY, itemCallNumberComponents.getString(CALL_NUMBER_PREFIX_KEY))
        .put(CALL_NUMBER_SUFFIX_KEY, itemCallNumberComponents.getString(CALL_NUMBER_SUFFIX_KEY));
    }

    return callNumberComponents;
  }

}
