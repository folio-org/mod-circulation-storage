package org.folio.service.event.handler;

import static io.vertx.core.Future.succeededFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class LocationDeleteEventHandler extends BaseInventoryEventHandler implements AsyncRecordHandler<String, String> {
  private static final Logger log = LogManager.getLogger(LocationDeleteEventHandler.class);
  private final Context context;

  public LocationDeleteEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    String eventType = payload.getString("type");

    if ("DELETE_ALL".equals(eventType)) {
      log.info("handle:: Received DELETE_ALL event, invalidating all location cache entries");
      invalidateAllLocationCache();
    } else {
      JsonObject oldObject = payload.getJsonObject("old");
      if (oldObject != null && oldObject.containsKey("id")) {
        String locationId = oldObject.getString("id");
        log.info("handle:: Received DELETE event for location id: {}", locationId);
        invalidateLocationCache(locationId);
      }
    }

    return succeededFuture(kafkaConsumerRecord.key());
  }
}




