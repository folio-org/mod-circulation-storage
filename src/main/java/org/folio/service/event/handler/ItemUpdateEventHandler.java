package org.folio.service.event.handler;

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

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ItemUpdateEventHandler extends UpdateEventAbstractHandler<Request> {

  private static final Logger log = LogManager.getLogger(ItemUpdateEventHandler.class);
  private static final String EFFECTIVE_SHELVING_ORDER_KEY = "effectiveShelvingOrder";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY = "effectiveCallNumberComponents";
  private static final String CALL_NUMBER_KEY = "callNumber";
  private static final String CALL_NUMBER_PREFIX_KEY = "prefix";
  private static final String CALL_NUMBER_SUFFIX_KEY = "suffix";

  private final Context context;

  @Override
  protected String supportedEventType() {
    return INVENTORY_ITEM_UPDATED.getPayloadType().name();
  }

  protected List<Change<Request>> collectRelevantChanges(JsonObject oldObject, JsonObject newObject) {
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
  protected Future<List<Request>> applyChanges(List<Change<Request>> changes,
    KafkaConsumerRecord<String, String> event, JsonObject oldObject, JsonObject newObject) {

    log.debug("applyChanges:: applying item-related changes");

    RequestRepository requestRepository = new RequestRepository(context, getKafkaHeaders(event));

    return findRequestsForItem(requestRepository, oldObject.getString("id"))
      .compose(requests -> applyDbUpdates(requests, changes, requestRepository));
  }

  private Future<List<Request>> findRequestsForItem(RequestRepository requestRepository, String itemId) {
    log.info("findRequestsByItemId:: fetching requests for item {}", itemId);

    return requestRepository.get(new Criterion(
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
