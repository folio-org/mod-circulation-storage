package org.folio.service.event;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.support.JsonPropertyWriter.write;
import static org.folio.support.LogEventPayloadField.LOG_EVENT_TYPE;
import static org.folio.support.kafka.topic.CirculationStorageKafkaTopic.LOG_RECORD;

import java.util.Map;
import java.util.UUID;

import org.folio.support.LogEventPayloadField;
import org.folio.support.exception.LogEventType;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Publishes {@code LOG_RECORD} domain events to Kafka.
 *
 * <p>This is part of the Phase 1 PubSub deprecation work (transport only):
 * it provides a Kafka producer capability equivalent to the existing
 * PubSub-based publishing, so that a future story can switch the actual
 * publishing call sites over to Kafka. It is not yet wired into any
 * production code path.</p>
 */
public class LogRecordEventPublisher {

  private final KafkaEventPublisher<String, JsonObject> eventPublisher;

  public LogRecordEventPublisher(Context vertxContext, Map<String, String> okapiHeaders) {
    this(new KafkaEventPublisher<>(vertxContext,
      LOG_RECORD.fullTopicName(tenantId(okapiHeaders))));
  }

  LogRecordEventPublisher(KafkaEventPublisher<String, JsonObject> eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  public Future<Void> publish(JsonObject payload, LogEventType logEventType,
      Map<String, String> okapiHeaders) {

    JsonObject context = new JsonObject().put(LogEventPayloadField.PAYLOAD.value(), payload);
    write(context, LOG_EVENT_TYPE.value(), logEventType.value());

    DomainEvent<JsonObject> event = DomainEvent.<JsonObject>builder()
      .id(UUID.randomUUID())
      .type(DomainEventType.CREATED)
      .tenant(tenantId(okapiHeaders))
      .timestamp(System.currentTimeMillis())
      .data(context)
      .build();

    return eventPublisher.publish(UUID.randomUUID().toString(), event, okapiHeaders);
  }

}
