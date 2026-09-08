package org.folio.service.event;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.kafka.services.KafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

public class KafkaEventPublisher<K> {

  private static final Logger log = getLogger(KafkaEventPublisher.class);

  private final String kafkaTopic;
  private final KafkaProducerManager producerManager;
  private final FailureHandler failureHandler;

  protected KafkaEventPublisher(KafkaTopic topic, Map<String, String> okapiHeaders, Context vertxContext) {
    this(topic, tenantId(okapiHeaders), vertxContext);
  }

  protected KafkaEventPublisher(KafkaTopic topic, String tenantId, Context vertxContext) {
    this(topic.fullTopicName(tenantId), createProducerManager(vertxContext));
  }

  protected KafkaEventPublisher(String topic, KafkaProducerManager producerManager) {
    this.kafkaTopic = topic;
    this.producerManager = producerManager;
    this.failureHandler = FailureHandler.noOperation();
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
      .kafkaPort(KafkaEnvironmentProperties.port())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .build();
    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

  public Future<Void> publish(K key, JsonObject payload, Map<String, String> okapiHeaders) {
    // Use payload.mapTo(Map.class) so KafkaProducerRecordBuilder serializes via Jackson correctly.
    // Passing a raw JsonObject would produce {"map":{...}} instead of the intended flat structure.
    return publish(key, payload.mapTo(Map.class), okapiHeaders);
  }

  public Future<Void> publish(K key, Object payload, Map<String, String> okapiHeaders) {
    log.info("publish:: key={}, topic={}", key, kafkaTopic);

    KafkaProducerRecord<K, String> producerRecord =
      new KafkaProducerRecordBuilder<K, Object>(tenantId(okapiHeaders))
        .key(key)
        .value(payload)
        .topic(kafkaTopic)
        .propagateOkapiHeaders(okapiHeaders)
        .build();

    return sendRecord(producerRecord, key);
  }

  private Future<Void> sendRecord(KafkaProducerRecord<K, String> record, K key) {
    KafkaProducer<K, String> producer = null;
    try {
      producer = producerManager.createShared(kafkaTopic);
      producer.send(record)
        .onSuccess(r -> log.info("sendRecord:: Succeeded sending event with key [{}]", key))
        .onFailure(cause -> {
          log.error("sendRecord:: Failed to send event with key [{}]", key, cause);
          failureHandler.handle(cause, record);
        })
        .eventually(producer::flush)
        .eventually(producer::close);
    } catch (Exception e) {
      log.error("sendRecord:: Exception initiating send for event with key [{}]", key, e);
      if (producer != null) {
        producer.close();
      }
      failureHandler.handle(e, record);
    }
    return Future.succeededFuture();
  }
}
