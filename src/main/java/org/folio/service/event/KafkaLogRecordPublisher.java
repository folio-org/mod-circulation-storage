package org.folio.service.event;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.support.kafka.topic.AuditKafkaTopic.LOG_RECORD;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

/**
 * Publishes LOG_RECORD events to the Kafka audit topic.
 *
 * <p>The message value is the raw log event JSON (not wrapped in a DomainEvent envelope),
 * matching the format expected by consumers:
 * {@code { "logEventType": "...", "payload": { ... } }}.
 */
public class KafkaLogRecordPublisher extends AbstractEventPublisher {

  private static final Logger log = getLogger(KafkaLogRecordPublisher.class);

  /** Package-private constructor for testing. */
  KafkaLogRecordPublisher(String kafkaTopic, KafkaProducerManager producerManager) {
    super(kafkaTopic, producerManager);
  }

  /** Production constructor. */
  public KafkaLogRecordPublisher(Context vertxContext, Map<String, String> okapiHeaders) {
    super(LOG_RECORD.fullTopicName(tenantId(okapiHeaders)), createProducerManager(vertxContext));
  }

  /**
   * Publishes a LOG_RECORD event to Kafka.
   *
   * @param key          Kafka record key (typically the request ID)
   * @param payload      the raw log event JSON: {@code { "logEventType": "...", "payload": {...} }}
   * @param okapiHeaders Okapi headers propagated as Kafka record headers
   * @return always-succeeded {@code Future<Void>}; send errors are logged but not propagated
   */
  public Future<Void> publish(String key, JsonObject payload, Map<String, String> okapiHeaders) {
    log.info("publish:: key={}, topic={}", key, kafkaTopic);

    // Use payload.mapTo(Map.class) so KafkaProducerRecordBuilder serializes via Jackson correctly.
    // Passing a raw JsonObject would produce {"map":{...}} instead of the intended flat structure.
    KafkaProducerRecord<String, String> producerRecord =
        new KafkaProducerRecordBuilder<String, Object>(tenantId(okapiHeaders))
            .key(key)
            .value(payload.mapTo(Map.class))
            .topic(kafkaTopic)
            .propagateOkapiHeaders(okapiHeaders)
            .build();

    return sendRecord(producerRecord, key, FailureHandler.noOperation());
  }
}
