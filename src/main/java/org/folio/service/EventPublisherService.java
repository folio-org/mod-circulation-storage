package org.folio.service;

import static org.folio.support.JsonPropertyWriter.write;
import static org.folio.support.LogEventPayloadField.LOG_EVENT_TYPE;

import java.util.Map;

import org.folio.service.event.LogRecordEventPublisher;
import org.folio.support.EventType;
import org.folio.support.LogEventPayloadField;
import org.folio.support.exception.LogEventType;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class EventPublisherService {

  private final PubSubPublishingService pubSubPublishingService;
  private final LogRecordEventPublisher logRecordEventPublisher;
  private final Map<String, String> okapiHeaders;

  public EventPublisherService(Vertx vertx, Map<String, String> okapiHeaders) {
    this(vertx, vertx.getOrCreateContext(), okapiHeaders);
  }

  public EventPublisherService(Vertx vertx, Context vertxContext, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.pubSubPublishingService = new PubSubPublishingService(vertx, okapiHeaders);
    this.logRecordEventPublisher = new LogRecordEventPublisher(vertxContext, okapiHeaders);
  }

  // Package-private for testing
  EventPublisherService(PubSubPublishingService pubSubPublishingService,
      LogRecordEventPublisher logRecordEventPublisher, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.pubSubPublishingService = pubSubPublishingService;
    this.logRecordEventPublisher = logRecordEventPublisher;
  }

  public Future<Void> publishLogRecord(JsonObject context, LogEventType payloadType) {
    log.debug("publishLogRecord:: publishing LOG_RECORD event to Kafka");
    logRecordEventPublisher.publish(context, payloadType, okapiHeaders);

    context = new JsonObject().put(LogEventPayloadField.PAYLOAD.value(), context);
    write(context, LOG_EVENT_TYPE.value(), payloadType.value());

    Promise<Void> promise = Promise.promise();
    pubSubPublishingService.publishEvent(EventType.LOG_RECORD.name(), context.encode())
      .thenAccept(r -> promise.complete());
    return promise.future();
  }

}

