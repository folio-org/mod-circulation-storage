package org.folio.service.event.handler;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.service.event.InventoryEventType.INVENTORY_ITEM_UPDATED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
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
public class ItemUpdateEventHandler implements AsyncRecordHandler<String, String> {

  private static final Logger log = LogManager.getLogger(ItemUpdateEventHandler.class);
  private static final String EFFECTIVE_SHELVING_ORDER_KEY = "effectiveShelvingOrder";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY = "effectiveCallNumberComponents";
  private static final String CALL_NUMBER_KEY = "callNumber";
  private static final String CALL_NUMBER_PREFIX_KEY = "prefix";
  private static final String CALL_NUMBER_SUFFIX_KEY = "suffix";

  private final Context context;

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> event) {
    final String eventKey = event.key();
    log.info("handle:: received event {}", eventKey);

    return processEvent(event)
      .onSuccess(r -> log.info("handle:: event {} processed successfully", eventKey))
      .onFailure(t -> log.error("handle:: failed to process event", t))
      .map(eventKey);
  }

  private Future<List<Request>> processEvent(KafkaConsumerRecord<String, String> event) {
    JsonObject payload = new JsonObject(event.value());

    String eventType = payload.getString("type");
    if (!INVENTORY_ITEM_UPDATED.getPayloadType().name().equals(eventType)) {
      log.info("updateRequestSearchIndex:: unsupported event type: {}", eventType);
      return succeededFuture();
    }

    JsonObject oldItem = payload.getJsonObject("old");
    JsonObject newItem = payload.getJsonObject("new");

    if (oldItem == null || newItem == null) {
      log.warn("updateRequestSearchIndex:: failed to find old and/or new item version");
      return succeededFuture();
    }

    List<Change<Request>> relevantChanges = collectRelevantChanges(oldItem, newItem);

    if (relevantChanges.isEmpty()) {
      log.info("updateRequestSearchIndex:: no relevant changes detected");
      return succeededFuture();
    }

    log.info("updateRequestSearchIndex:: relevant changes detected");
    RequestRepository requestRepository = createRequestRepository(event);

    return findRequestsForItem(requestRepository, oldItem.getString("id"))
      .compose(requests -> updateRequests(requests, relevantChanges, requestRepository));
  }

  private static List<Change<Request>> collectRelevantChanges(JsonObject oldItem, JsonObject newItem) {
    List<Change<Request>> changes = new ArrayList<>();

    // compare call number components
    JsonObject oldCallNumberComponents = extractCallNumberComponents(oldItem);
    JsonObject newCallNumberComponents = extractCallNumberComponents(newItem);
    if (notEqual(oldCallNumberComponents, newCallNumberComponents)) {
      changes.add(new Change<>(request -> request.getSearchIndex()
        .setCallNumberComponents(newCallNumberComponents.mapTo(CallNumberComponents.class))));
    }

    // compare shelving order
    String oldShelvingOrder = oldItem.getString(EFFECTIVE_SHELVING_ORDER_KEY);
    String newShelvingOrder = newItem.getString(EFFECTIVE_SHELVING_ORDER_KEY);
    if (notEqual(oldShelvingOrder, newShelvingOrder)) {
      changes.add(new Change<>(request -> request.getSearchIndex().setShelvingOrder(newShelvingOrder)));
    }

    return changes;
  }

  private Future<List<Request>> findRequestsForItem(RequestRepository requestRepository, String itemId) {
    log.info("findRequestsByItemId:: fetching requests for item {}", itemId);

    return requestRepository.get(new Criterion(
      new Criteria()
        .addField("'itemId'")
        .setOperation("=")
        .setVal(itemId)));
  }

  private Future<List<Request>> updateRequests(List<Request> requests,
    Collection<Change<Request>> changes, RequestRepository requestRepository) {

    if (requests.isEmpty()) {
      log.info("updateRequests:: no requests found, nothing to update");
      return succeededFuture(requests);
    }

    log.info("updateRequests:: {} requests found, applying changes", requests.size());
    requests.forEach(request -> changes.forEach(change -> change.apply(request)));

    log.info("updateRequests:: persisting changes");
    return requestRepository.update(requests).map(requests);
  }

  private RequestRepository createRequestRepository(KafkaConsumerRecord<String, String> event) {
    var headers = new CaseInsensitiveMap<>(kafkaHeadersToMap(event.headers()));
    return new RequestRepository(context, headers);
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

  @RequiredArgsConstructor
  private static class Change<T> {
    private final Consumer<T> changeConsumer;

    public void apply(T target) {
      changeConsumer.accept(target);
    }
  }

}
