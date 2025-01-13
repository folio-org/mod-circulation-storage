package org.folio.service.event.handler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestRepository;
import org.folio.service.event.handler.processor.ItemLocationUpdateProcessorForRequest;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

public class LocationUpdateEventHandler implements AsyncRecordHandler<String, String> {
  private final Context context;
  public LocationUpdateEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    CaseInsensitiveMap<String, String> headers =
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));

    ItemLocationUpdateProcessorForRequest itemLocationUpdateProcessorForRequest =
            new ItemLocationUpdateProcessorForRequest(new RequestRepository(context, headers));

    return itemLocationUpdateProcessorForRequest.run(kafkaConsumerRecord.key(), payload);
  }
}
