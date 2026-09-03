package org.folio.service.event;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

public abstract class AbstractEventPublisher {

  private static final Logger log = getLogger(AbstractEventPublisher.class);

  protected final String kafkaTopic;
  protected final KafkaProducerManager producerManager;

  protected AbstractEventPublisher(String kafkaTopic, KafkaProducerManager producerManager) {
    this.kafkaTopic = kafkaTopic;
    this.producerManager = producerManager;
  }

  protected static KafkaProducerManager createProducerManager(Context vertxContext) {
    var kafkaConfig = KafkaConfig.builder()
        .kafkaPort(KafkaEnvironmentProperties.port())
        .kafkaHost(KafkaEnvironmentProperties.host())
        .build();
    return new SimpleKafkaProducerManager(vertxContext.owner(), kafkaConfig);
  }

  protected <K> Future<Void> sendRecord(KafkaProducerRecord<K, String> record, K key,
      FailureHandler failureHandler) {
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
