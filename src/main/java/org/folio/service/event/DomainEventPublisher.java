package org.folio.service.event;

import static org.apache.logging.log4j.LogManager.getLogger;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.kafka.services.KafkaProducerRecordBuilder;

public class DomainEventPublisher<K, T> {

  private static final Logger log = getLogger(DomainEventPublisher.class);

  private final String kafkaTopic;
  private final KafkaProducerManager producerManager;
  private final FailureHandler failureHandler;

  DomainEventPublisher(String kafkaTopic, KafkaProducerManager producerManager,
      FailureHandler failureHandler) {
    this.kafkaTopic = kafkaTopic;
    this.producerManager = producerManager;
    this.failureHandler = failureHandler;
  }

  public DomainEventPublisher(Context vertxContext, String kafkaTopic, FailureHandler failureHandler) {
    this(kafkaTopic, createProducerManager(vertxContext), failureHandler);
  }

  public Future<Void> publish(K key, DomainEvent<T> event, Map<String, String> okapiHeaders) {
    log.info("Publishing event: key = {}, eventId = {}, type = {}, topic = {}",
        key, event.getId(), event.getType(), kafkaTopic);

    KafkaProducerRecordBuilder<K, DomainEvent<T>> builder = new KafkaProducerRecordBuilder<>();
    KafkaProducerRecord<K, String> producerRecord = builder
        .key(key).value(event).topic(kafkaTopic).propagateOkapiHeaders(okapiHeaders)
        .build();

    KafkaProducer<K, String> producer = getOrCreateProducer();

    log.debug("Sending event to Kafka: kafkaRecord = [{}]", producerRecord);

    return producer.send(producerRecord)
        .<Void>map(notUsed -> null)
        .onComplete(result -> {
          producer.end(par -> producer.close());

          if (result.failed()) {
            log.error("Unable to send domain event with key [{}], kafka record [{}]",
                key, producerRecord, result.cause());

            failureHandler.handle(result.cause(), producerRecord);
          }
        });
  }

  private KafkaProducer<K, String> getOrCreateProducer() {
    return getOrCreateProducer("");
  }

  private KafkaProducer<K, String> getOrCreateProducer(String prefix) {
    return producerManager.createShared(prefix + kafkaTopic);
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
        .kafkaPort(KafkaEnvironmentProperties.port())
        .kafkaHost(KafkaEnvironmentProperties.host())
        .build();

    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

}
