package org.folio.service.event;

import static org.apache.logging.log4j.LogManager.getLogger;

import java.util.Map;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.apache.logging.log4j.Logger;

import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.service.kafka.KafkaProducerRecordBuilder;
import org.folio.service.kafka.KafkaProperties;
import org.folio.service.kafka.topic.KafkaTopic;

public class DomainEventPublisher<K, T> {

  private static final Logger log = getLogger(DomainEventPublisher.class);

  private final KafkaTopic kafkaTopic;
  private final KafkaProducerManager producerManager;
  private final FailureHandler failureHandler;


  DomainEventPublisher(KafkaTopic kafkaTopic, KafkaProducerManager producerManager,
      FailureHandler failureHandler) {
    this.kafkaTopic = kafkaTopic;
    this.producerManager = producerManager;
    this.failureHandler = failureHandler;
  }

  public DomainEventPublisher(Context vertxContext, KafkaTopic kafkaTopic, FailureHandler failureHandler) {
    this(kafkaTopic, createProducerManager(vertxContext), failureHandler);
  }

  public Future<Void> publish(K key, DomainEvent<T> event, Map<String, String> okapiHeaders) {
    KafkaProducerRecordBuilder<K, DomainEvent<T>> builder = new KafkaProducerRecordBuilder<>();
    KafkaProducerRecord<K, String> producerRecord = builder
        .key(key).value(event).topic(kafkaTopic).propagateOkapiHeaders(okapiHeaders)
        .build();

    KafkaProducer<K, String> producer = getOrCreateProducer();

    // TODO (Dima Tkachenko): temporary level up the severity to view events in the log for testing purposes
    log.info("Sending domain event with key [{}], kafka record [{}]", key, producerRecord);

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
    return producerManager.createShared(prefix + kafkaTopic.getQualifiedName());
  }

  private static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
        .kafkaPort(KafkaProperties.getPort())
        .kafkaHost(KafkaProperties.getHost())
        .build();

    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

}
