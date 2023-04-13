package org.folio.rest.impl;

import static java.lang.System.getenv;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_ENV;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_HOST;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_MAX_REQUEST_SIZE;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_PORT;
import static org.folio.support.kafka.KafkaConfigConstants.KAFKA_REPLICATION_FACTOR;
import static org.folio.support.kafka.KafkaConfigConstants.OKAPI_URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.EventConsumerVerticle;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.rest.resource.interfaces.InitAPI;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class InitAPIImpl implements InitAPI {

  private static final Logger log = LogManager.getLogger(InitAPIImpl.class);
  private static final String DEFAULT_OKAPI_URL = "http://okapi:9130";
  private static final int DEFAULT_MAX_REQUEST_SIZE = 4000000;

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    deployEventConsumerVerticle(vertx)
      .map(true)
      .onSuccess(r -> log.info("init:: initialization complete"))
      .onFailure(t -> log.error("init:: initialization failed", t))
      .onComplete(resultHandler);
  }

  private static Future<String> deployEventConsumerVerticle(Vertx vertx) {
    JsonObject kafkaConfig = new JsonObject()
      .put(KAFKA_HOST, KafkaEnvironmentProperties.host())
      .put(KAFKA_PORT, KafkaEnvironmentProperties.port())
      .put(KAFKA_REPLICATION_FACTOR, KafkaEnvironmentProperties.replicationFactor())
      .put(KAFKA_ENV, KafkaEnvironmentProperties.environment())
      .put(OKAPI_URL, getenv().getOrDefault(OKAPI_URL, DEFAULT_OKAPI_URL))
      .put(KAFKA_MAX_REQUEST_SIZE, getenv().getOrDefault(KAFKA_MAX_REQUEST_SIZE,
        String.valueOf(DEFAULT_MAX_REQUEST_SIZE)));

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setWorker(true)
      .setConfig(kafkaConfig);

    return vertx.deployVerticle(EventConsumerVerticle.class, deploymentOptions)
      .onSuccess(r -> log.info("deployEventConsumerVerticle:: deployment complete"))
      .onFailure(t -> log.error("deployEventConsumerVerticle:: deployment failed", t));
  }

}
