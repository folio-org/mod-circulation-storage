package org.folio.service.event;

import static org.apache.logging.log4j.LogManager.getLogger;

import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

public class DomainEventPublisher<K, T> extends AbstractEventPublisher {

  private static final Logger log = getLogger(DomainEventPublisher.class);

  private final FailureHandler failureHandler;

  DomainEventPublisher(String kafkaTopic, KafkaProducerManager producerManager,
      FailureHandler failureHandler) {
    super(kafkaTopic, producerManager);
    this.failureHandler = failureHandler;
  }

  public DomainEventPublisher(Context vertxContext, String kafkaTopic,
      FailureHandler failureHandler) {
    this(kafkaTopic, createProducerManager(vertxContext), failureHandler);
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

    return sendRecord(producerRecord, key, failureHandler);
  }
}
