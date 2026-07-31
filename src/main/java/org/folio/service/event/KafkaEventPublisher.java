package org.folio.service.event;

import java.util.Map;

import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class KafkaEventPublisher<K, T> {

  private final String kafkaTopic;
  private final KafkaProducerManager producerManager;

  public KafkaEventPublisher(Context vertxContext, String kafkaTopic) {
    this(kafkaTopic, createProducerManager(vertxContext));
  }

  public Future<Void> publish(K key, DomainEvent<T> event, Map<String, String> okapiHeaders) {
    log.info("publish:: key = {}, eventId = {}, type = {}, topic = {}", key, event.getId(),
      event.getType(), kafkaTopic);

    KafkaProducerRecord<K, String> producerRecord =
      new KafkaProducerRecordBuilder<K, DomainEvent<T>>(TenantTool.tenantId(okapiHeaders))
        .key(key)
        .value(event)
        .topic(kafkaTopic)
        .propagateOkapiHeaders(okapiHeaders)
        .build();

    KafkaProducer<K, String> producer = null;
    try {
      producer = producerManager.createShared(kafkaTopic);
      log.debug("publish:: producer created, sending the record...");

      producer.send(producerRecord)
        .onSuccess(r -> log.info("publish:: published event with key {}", key))
        .onFailure(cause -> log.error("publish:: failed to publish event with key {}", key, cause))
        .eventually(producer::flush)
        .eventually(producer::close);
    } catch (Exception e) {
      log.error("publish:: failed to publish event with key {}", key, e);
      if (producer != null) {
        log.debug("publish:: trying to close producer for event {}", key);
        producer.close();
      }
    }

    return Future.succeededFuture();
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
      .kafkaPort(KafkaEnvironmentProperties.port())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .build();

    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

}
