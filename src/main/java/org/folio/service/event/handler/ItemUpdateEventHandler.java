package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.service.event.handler.processor.ItemUpdateProcessorForRequest;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ItemUpdateEventHandler implements AsyncRecordHandler<String, String> {
  private final Context context;

  public ItemUpdateEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {

    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());

    ItemUpdateProcessorForRequest itemUpdateProcessorForRequest =
      new ItemUpdateProcessorForRequest(context);

    return itemUpdateProcessorForRequest.run(kafkaConsumerRecord.key(),
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers())), payload);
  }
}
