package org.folio.service.event.handler.processor;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.service.event.InventoryEventType;

import io.vertx.core.json.JsonObject;

public abstract class UpdateEventProcessor<T> extends EventProcessor<T> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  protected UpdateEventProcessor(InventoryEventType supportedEventType) {
    super(supportedEventType);
  }

  @Override
  protected boolean validatePayload(JsonObject payload) {
    JsonObject oldObject = payload.getJsonObject("old");
    JsonObject newObject = payload.getJsonObject("new");

    if (oldObject == null || newObject == null) {
      log.warn("validatePayload:: failed to find old and/or new version");
      return false;
    }

    return true;
  }
}
