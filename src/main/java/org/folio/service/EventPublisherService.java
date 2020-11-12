package org.folio.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.support.EventType;
import org.folio.support.JsonPropertyWriter;
import org.folio.support.LogEventPayloadField;
import org.folio.support.exception.LogEventType;

import java.util.Map;

import static org.folio.support.LogEventPayloadField.LOG_EVENT_TYPE;

public class EventPublisherService {

  private final PubSubPublishingService pubSubPublishingService;

  public EventPublisherService(Vertx vertx, Map<String, String> okapiHeaders) {
    pubSubPublishingService = new PubSubPublishingService(vertx, okapiHeaders);
  }

  public Future<Void> publishLogRecord(JsonObject context, LogEventType payloadType) {
    JsonPropertyWriter.write(context, LOG_EVENT_TYPE.value(), payloadType.value());
    context = new JsonObject().put(LogEventPayloadField.PAYLOAD.value(), context.encode());
    Promise<Void> promise = Promise.promise();
    pubSubPublishingService.publishEvent(EventType.LOG_RECORD.name(), context.encode())
      .thenAccept(r -> promise.complete());
    return promise.future();
  }

}
