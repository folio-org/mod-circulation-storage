package org.folio;

import static java.util.stream.Collectors.toList;
import static org.folio.rest.tools.utils.ModuleName.getModuleName;
import static org.folio.rest.tools.utils.ModuleName.getModuleVersion;
import static org.folio.service.event.InventoryEventType.INVENTORY_ITEM_UPDATED;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_ENV;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_HOST;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_MAX_REQUEST_SIZE;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_PORT;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_REPLICATION_FACTOR;
import static org.folio.support.kafka.KafkaConfigConstants.OKAPI_URL;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.kafka.services.KafkaTopic;
import org.folio.service.event.InventoryEventType;
import org.folio.service.event.handler.ItemUpdateEventHandler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class EventConsumerVerticle extends AbstractVerticle {

  private static final Logger log = LogManager.getLogger(EventConsumerVerticle.class);
  private static final int DEFAULT_LOAD_LIMIT = 5;
  private static final String TENANT_ID_PATTERN = "\\w+";
  private static final String MODULE_ID = getModuleId();

  private final List<KafkaConsumerWrapper<String, String>> consumers = new ArrayList<>();

  @Override
  public void start(Promise<Void> promise) {
    log.info("start:: starting verticle");

    createConsumers()
      .onSuccess(v -> log.info("start:: verticle started"))
      .onFailure(t -> log.error("start:: verticle start failed", t))
      .onComplete(promise);
  }

  @Override
  public void stop(Promise<Void> promise) {
    log.info("stop:: stopping verticle");

    stopConsumers()
      .onSuccess(v -> log.info("stop:: verticle stopped"))
      .onFailure(t -> log.error("stop:: verticle stop failed", t))
      .onComplete(promise);
  }

  private Future<Void> stopConsumers() {
    return CompositeFuture.all(
      consumers.stream()
        .map(KafkaConsumerWrapper::stop)
        .collect(toList()))
      .onSuccess(v -> log.info("stop:: event consumers stopped"))
      .onFailure(t -> log.error("stop:: failed to stop event consumers", t))
      .mapEmpty();
  }

  private Future<Void> createConsumers() {
    final KafkaConfig config = getKafkaConfig();

    return createInventoryEventConsumer(INVENTORY_ITEM_UPDATED, config,  new ItemUpdateEventHandler(context))
      .mapEmpty();
  }

  private Future<KafkaConsumerWrapper<String, String>> createInventoryEventConsumer(
    InventoryEventType eventType, KafkaConfig kafkaConfig,
    AsyncRecordHandler<String, String> handler) {

    SubscriptionDefinition subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(eventType.name())
      .subscriptionPattern(buildSubscriptionPattern(eventType.getKafkaTopic(), kafkaConfig))
      .build();

    return createConsumer(kafkaConfig, subscriptionDefinition, handler);
  }

  private Future<KafkaConsumerWrapper<String, String>> createConsumer(KafkaConfig kafkaConfig,
    SubscriptionDefinition subscriptionDefinition, AsyncRecordHandler<String, String> recordHandler) {

    var consumer = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(DEFAULT_LOAD_LIMIT)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    return consumer.start(recordHandler, MODULE_ID)
      .onSuccess(v -> consumers.add(consumer))
      .map(consumer);
  }

  private KafkaConfig getKafkaConfig() {
    JsonObject vertxConfig = vertx.getOrCreateContext().config();

    KafkaConfig config = KafkaConfig.builder()
      .envId(vertxConfig.getString(KAFKA_ENV))
      .kafkaHost(vertxConfig.getString(KAFKA_HOST))
      .kafkaPort(vertxConfig.getString(KAFKA_PORT))
      .okapiUrl(vertxConfig.getString(OKAPI_URL))
      .replicationFactor(Integer.parseInt(vertxConfig.getString(KAFKA_REPLICATION_FACTOR)))
      .maxRequestSize(Integer.parseInt(vertxConfig.getString(KAFKA_MAX_REQUEST_SIZE)))
      .build();

    log.info("getKafkaConfig:: {}", config);

    return config;
  }

  private static String getModuleId() {
    return getModuleName().replace("_", "-") + "-" + getModuleVersion();
  }

  private static String buildSubscriptionPattern(KafkaTopic kafkaTopic, KafkaConfig kafkaConfig) {
    return kafkaTopic.fullTopicName(kafkaConfig, TENANT_ID_PATTERN);
  }

}
