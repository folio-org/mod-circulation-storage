package org.folio.service.event.handler;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.service.event.InventoryEventType.INVENTORY_ITEM_UPDATED;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.AbstractRepository;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.RequiredArgsConstructor;

public abstract class UpdateEventAbstractHandler<T> implements AsyncRecordHandler<String, String> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> event) {
    final String eventKey = event.key();
    log.info("handle:: received event {}", eventKey);

    return processEvent(event)
      .onSuccess(r -> log.info("handle:: event {} processed successfully", eventKey))
      .onFailure(t -> log.error("handle:: failed to process event", t))
      .map(eventKey);
  }

  protected Future<List<T>> processEvent(KafkaConsumerRecord<String, String> event) {
    JsonObject payload = new JsonObject(event.value());

    String eventType = payload.getString("type");
    if (!INVENTORY_ITEM_UPDATED.getPayloadType().name().equals(eventType)) {
      log.info("processEvent:: unsupported event type: {}", eventType);
      return succeededFuture();
    }

    JsonObject oldObject = payload.getJsonObject("old");
    JsonObject newObject = payload.getJsonObject("new");

    if (oldObject == null || newObject == null) {
      log.warn("processEvent:: failed to find old and/or new item version");
      return succeededFuture();
    }

    List<Change<T>> relevantChanges = collectRelevantChanges(oldObject, newObject);

    if (relevantChanges.isEmpty()) {
      log.info("processEvent:: no relevant changes detected");
      return succeededFuture();
    }

    log.info("processEvent:: {} relevant changes detected, applying", relevantChanges::size);
    return applyChanges(relevantChanges, event, oldObject, newObject);
  }

  protected abstract List<Change<T>> collectRelevantChanges(JsonObject oldObject,
    JsonObject newObject);

  protected abstract Future<List<T>> applyChanges(List<Change<T>> changes,
    KafkaConsumerRecord<String, String> event, JsonObject oldObject, JsonObject newObject);

  protected CaseInsensitiveMap<String, String> getKafkaHeaders(
    KafkaConsumerRecord<String, String> event) {

    return new CaseInsensitiveMap<>(kafkaHeadersToMap(event.headers()));
  }

  protected <R> Future<List<R>> applyDbUpdates(List<R> objects, Collection<Change<R>> changes,
    AbstractRepository<R> repository) {

    if (objects.isEmpty()) {
      log.info("applyDbUpdates:: no objects to update found, nothing to update");
      return succeededFuture(objects);
    }

    log.info("applyDbUpdates:: {} objects to update found, applying changes", objects.size());
    objects.forEach(obj -> changes.forEach(change -> change.apply(obj)));

    log.info("applyDbUpdates:: persisting changes");
    return repository.update(objects).map(objects);
  }

  @RequiredArgsConstructor
  protected static class Change<T> {
    private final Consumer<T> changeConsumer;

    public void apply(T target) {
      changeConsumer.accept(target);
    }
  }
}
