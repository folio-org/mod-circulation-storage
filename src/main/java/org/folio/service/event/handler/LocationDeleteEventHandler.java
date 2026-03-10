package org.folio.service.event.handler;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class LocationDeleteEventHandler extends BaseInventoryEventHandler implements AsyncRecordHandler<String, String> {
  private final Context context;

  public LocationDeleteEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    CaseInsensitiveMap<String, String> headers =
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));

    JsonObject oldObject = payload.getJsonObject("old");
    if (oldObject != null && oldObject.containsKey("id")) {
      String locationId = oldObject.getString("id");
      invalidateLocationCache(locationId);
    }

    return succeededFuture(kafkaConsumerRecord.key());
  }
}

