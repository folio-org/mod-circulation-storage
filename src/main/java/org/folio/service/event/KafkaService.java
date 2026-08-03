package org.folio.service.event;

import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.kafka.services.KafkaTopic;
import org.folio.support.kafka.topic.CirculationStorageKafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaService {

  private final KafkaAdminClientService kafkaAdminClientService;

  public KafkaService(Vertx vertx) {
    this.kafkaAdminClientService = new KafkaAdminClientService(vertx);
  }

  // Package-private for testing
  KafkaService(KafkaAdminClientService kafkaAdminClientService) {
    this.kafkaAdminClientService = kafkaAdminClientService;
  }

  public Future<Void> createCirculationStorageTopics(String tenantId) {
    return createTopics(CirculationStorageKafkaTopic.values(), tenantId);
  }

  public Future<Void> createTopics(KafkaTopic[] topics, String tenantId) {
    log.info("createTopics:: tenant={}, topics={}", tenantId, topics);
    return kafkaAdminClientService.createKafkaTopics(topics, tenantId);
  }

  public Future<Void> deleteCirculationStorageTopics(String tenantId) {
    return deleteTopics(CirculationStorageKafkaTopic.values(), tenantId);
  }

  public Future<Void> deleteTopics(KafkaTopic[] topics, String tenantId) {
    log.info("deleteTopics:: tenant={}, topics={}", tenantId, topics);
    return kafkaAdminClientService.deleteKafkaTopics(topics, tenantId);
  }

  public KafkaEventPublisher<String, JsonObject> createPublisher(CirculationStorageKafkaTopic topic,
    Context context, String tenantId) {

    log.info("createPublisher:: tenant={}, topic={}", tenantId, topic);
    return new KafkaEventPublisher<>(context, topic.fullTopicName(tenantId));
  }

}
