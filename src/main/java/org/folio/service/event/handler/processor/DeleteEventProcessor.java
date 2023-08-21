package org.folio.service.event.handler.processor;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AbstractRepository;
import org.folio.service.event.InventoryEventType;

import io.vertx.core.json.JsonObject;

public abstract class DeleteEventProcessor<T> extends EventProcessor<T> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected DeleteEventProcessor(InventoryEventType supportedEventType,
    AbstractRepository<T> repository) {

    super(supportedEventType, repository);
  }

  @Override
  protected boolean validatePayload(JsonObject payload) {
    JsonObject oldObject = payload.getJsonObject("old");

    if (oldObject == null) {
      log.warn("validatePayload:: failed to find old version");
      return false;
    }

    return true;
  }
}
