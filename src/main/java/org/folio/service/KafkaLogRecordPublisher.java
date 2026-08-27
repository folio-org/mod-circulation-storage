package org.folio.service;

import static org.apache.logging.log4j.LogManager.getLogger;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.support.kafka.topic.AuditKafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

/**
 * Publishes LOG_RECORD events to the Kafka audit topic.
 *
 * <p>The message value is the raw log event JSON (not wrapped in a DomainEvent envelope),
 * matching the format expected by consumers:
 * {@code { "logEventType": "...", "payload": { ... } }}.
 */
public class KafkaLogRecordPublisher {

  private static final Logger log = getLogger(KafkaLogRecordPublisher.class);

  private final String kafkaTopic;
  private final KafkaProducerManager producerManager;

  /** Package-private constructor for testing. */
  KafkaLogRecordPublisher(String kafkaTopic, KafkaProducerManager producerManager) {
    this.kafkaTopic = kafkaTopic;
    this.producerManager = producerManager;
  }

  /** Production constructor. */
  public KafkaLogRecordPublisher(Context vertxContext, Map<String, String> okapiHeaders) {
    this.kafkaTopic = AuditKafkaTopic.LOG_RECORD.fullTopicName(TenantTool.tenantId(okapiHeaders));
    this.producerManager = new SimpleKafkaProducerManager(vertxContext.owner(),
        KafkaConfig.builder()
            .kafkaPort(KafkaEnvironmentProperties.port())
            .kafkaHost(KafkaEnvironmentProperties.host())
            .build());
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

    KafkaProducerRecord<String, String> producerRecord =
        new KafkaProducerRecordBuilder<String, JsonObject>(TenantTool.tenantId(okapiHeaders))
            .key(key)
            .value(payload)
            .topic(kafkaTopic)
            .propagateOkapiHeaders(okapiHeaders)
            .build();

    KafkaProducer<String, String> producer = null;
    try {
      producer = producerManager.createShared(kafkaTopic);
      producer.send(producerRecord)
          .onSuccess(r -> log.info("publish:: Succeeded sending LOG_RECORD event with key [{}]", key))
          .onFailure(cause -> log.error("publish:: Failed to send LOG_RECORD event with key [{}]",
              key, cause))
          .eventually(producer::flush)
          .eventually(producer::close);
    } catch (Exception e) {
      log.error("publish:: Exception initiating send for LOG_RECORD event with key [{}]", key, e);
      if (producer != null) {
        producer.close();
      }
    }

    return Future.succeededFuture();
  }
}
