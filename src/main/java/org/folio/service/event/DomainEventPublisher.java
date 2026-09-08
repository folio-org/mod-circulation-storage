package org.folio.service.event;

import java.util.Map;

import org.folio.kafka.services.KafkaTopic;

import io.vertx.core.Context;

public class DomainEventPublisher<K, T> extends KafkaEventPublisher<K> {

  protected DomainEventPublisher(KafkaTopic topic, Map<String, String> okapiHeaders, Context vertxContext) {
    super(topic, okapiHeaders, vertxContext);
  }

  DomainEventPublisher(KafkaTopic topic, String tenantId, Context vertxContext) {
    super(topic, tenantId, vertxContext);
  }

}
