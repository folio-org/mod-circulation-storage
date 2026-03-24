package org.folio.service.event.handler;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.rest.client.InventoryStorageClient;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class LocationDeleteEventHandler implements AsyncRecordHandler<String, String> {
  private static final Logger log = LogManager.getLogger(LocationDeleteEventHandler.class);

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    String tenantId = new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()))
      .get(OKAPI_HEADER_TENANT);
    String eventType = payload.getString("type");

    if ("DELETE_ALL".equals(eventType)) {
      log.info("handle:: Received DELETE_ALL event, invalidating all location cache entries");
      InventoryStorageClient.invalidateAllLocations();
    } else {
      JsonObject oldObject = payload.getJsonObject("old");
      if (oldObject != null && oldObject.containsKey("id")) {
        String locationId = oldObject.getString("id");
        log.info("handle:: Received DELETE event for location id: {}", locationId);
        InventoryStorageClient.invalidateLocation(tenantId, locationId);
      }
    }

    return succeededFuture(kafkaConsumerRecord.key());
  }
}
