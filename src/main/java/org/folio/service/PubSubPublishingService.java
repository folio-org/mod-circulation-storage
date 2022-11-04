package org.folio.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventMetadata;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.PubSubClientUtils;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class PubSubPublishingService {
  private static final Logger logger = LogManager.getLogger();

  private final Map<String, String> okapiHeaders;
  private final Vertx vertx;
  private final Context context;

  public PubSubPublishingService(Vertx vertx, Map<String, String> okapiHeaders) {
    this.vertx = vertx;
    context = vertx.getOrCreateContext();
    this.okapiHeaders = okapiHeaders;
  }

  public CompletableFuture<Boolean> publishEvent(String eventType, String payload) {
    OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders, vertx);
    Event event = new Event().withId(UUID.randomUUID()
      .toString())
      .withEventType(eventType)
      .withEventPayload(payload)
      .withEventMetadata(new EventMetadata().withPublishedBy(PubSubClientUtils.getModuleId())
        .withTenantId(params.getTenantId())
        .withEventTTL(1));

    final CompletableFuture<Boolean> publishResult = new CompletableFuture<>();

    context.runOnContext(v -> PubSubClientUtils.sendEventMessage(event, params)
      .whenComplete((result, throwable) -> {
        if (Boolean.TRUE.equals(result)) {
          logger.debug("Event published successfully. ID: {}, type: {}, payload: {}", event.getId(), event.getEventType(),
              event.getEventPayload());
          publishResult.complete(true);
        } else {
          logger.error("Failed to publish event. ID: {}, type: {}, payload: {}", throwable, event.getId(), event.getEventType(),
              event.getEventPayload());

          if (throwable == null) {
            publishResult.complete(false);
          } else {
            publishResult.completeExceptionally(throwable);
          }
        }
      }));

    return publishResult;
  }
}
