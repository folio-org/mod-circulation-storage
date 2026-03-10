package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestRepository;
import org.folio.service.event.handler.processor.ItemLocationUpdateProcessorForRequest;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class LocationUpdateEventHandler extends BaseInventoryEventHandler implements AsyncRecordHandler<String, String> {

  private final Context context;

  public LocationUpdateEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    CaseInsensitiveMap<String, String> headers =
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));

    // Invalidate cache for updated location
    JsonObject newObject = payload.getJsonObject("new");
    if (newObject != null && newObject.containsKey("id")) {
      String locationId = newObject.getString("id");
      invalidateLocationCache(locationId);
    }

    RequestRepository requestRepository = new RequestRepository(context, headers,
      getLocationCache(), getServicePointCache());

    ItemLocationUpdateProcessorForRequest itemLocationUpdateProcessorForRequest =
      new ItemLocationUpdateProcessorForRequest(requestRepository);

    return itemLocationUpdateProcessorForRequest.run(kafkaConsumerRecord.key(), payload);
  }
}
