package org.folio.service.event.handler.processor;

import static io.vertx.core.Future.succeededFuture;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AbstractRepository;
import org.folio.service.event.InventoryEventType;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

public abstract class EventProcessor<T> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected final InventoryEventType supportedEventType;
  protected AbstractRepository<T> repository;

  protected EventProcessor(InventoryEventType supportedEventType, AbstractRepository<T> repository) {
    this.supportedEventType = supportedEventType;
    this.repository = repository;
  }

  public Future<String> run(String eventKey, JsonObject payload) {
    log.info("run:: received event {}", eventKey);

    return processEvent(payload)
      .onSuccess(r -> log.info("handle:: event {} processed successfully", eventKey))
      .onFailure(t -> log.error("handle:: failed to process event", t))
      .map(eventKey);
  }

  private Future<List<T>> processEvent(JsonObject payload) {
    String eventType = payload.getString("type");
    if (!supportedEventType.getPayloadType().name().equals(eventType)) {
      log.info("processEvent:: unsupported event type: {}", eventType);
      return succeededFuture();
    }

    if (!validatePayload(payload)) {
      log.warn("processEvent:: payload validation failed");
      return succeededFuture();
    }

    List<Change<T>> relevantChanges = collectRelevantChanges(payload);

    if (relevantChanges.isEmpty()) {
      log.info("processEvent:: no relevant changes detected");
      return succeededFuture();
    }

    log.info("processEvent:: {} relevant changes detected, applying", relevantChanges::size);
    return applyChanges(relevantChanges, payload);
  }

  protected abstract boolean validatePayload(JsonObject payload);

  protected abstract List<Change<T>> collectRelevantChanges(JsonObject payload);

  private Future<List<T>> applyChanges(List<Change<T>> changes, JsonObject payload) {
    log.debug("applyChanges:: applying item-related changes");

    JsonObject oldObject = payload.getJsonObject("old");

    return findObjectsToBeUpdated(oldObject.getString("id"))
      .compose(requests -> applyDbUpdates(requests, changes));
  }

  protected abstract Future<List<T>> findObjectsToBeUpdated(String oldObjectId);

  protected Future<List<T>> applyDbUpdates(List<T> objects, Collection<Change<T>> changes) {
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
