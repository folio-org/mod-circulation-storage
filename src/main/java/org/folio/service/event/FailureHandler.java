package org.folio.service.event;

import io.vertx.kafka.client.producer.KafkaProducerRecord;

public interface FailureHandler {

  <K> void handle(Throwable error, KafkaProducerRecord<K, String> producerRecord);

  static FailureHandler noOperation() {
    return new FailureHandler() {

      @Override
      public <K> void handle(Throwable error, KafkaProducerRecord<K, String> producerRecord) {

      }
    };
  }

}
