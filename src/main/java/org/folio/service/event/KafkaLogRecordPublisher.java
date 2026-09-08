package org.folio.service.event;

import static org.folio.support.kafka.topic.AuditKafkaTopic.LOG_RECORD;

import java.util.Map;

import org.folio.kafka.KafkaProducerManager;

import io.vertx.core.Context;

/**
 * Publishes LOG_RECORD events to the Kafka audit topic.
 *
 * <p>The message value is the raw log event JSON (not wrapped in a DomainEvent envelope),
 * matching the format expected by consumers:
 * {@code { "logEventType": "...", "payload": { ... } }}.
 */
public class KafkaLogRecordPublisher extends KafkaEventPublisher<String> {

  /** Package-private constructor for testing. */
  KafkaLogRecordPublisher(String kafkaTopic, KafkaProducerManager producerManager) {
    super(kafkaTopic, producerManager);
  }

  /** Production constructor. */
  public KafkaLogRecordPublisher(Context vertxContext, Map<String, String> okapiHeaders) {
    super(LOG_RECORD, okapiHeaders, vertxContext);
  }

}
