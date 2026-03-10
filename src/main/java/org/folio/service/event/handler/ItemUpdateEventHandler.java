package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestRepository;
import org.folio.rest.client.InventoryStorageClient;
import org.folio.service.event.handler.processor.ItemUpdateProcessorForRequest;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ItemUpdateEventHandler extends BaseInventoryEventHandler implements AsyncRecordHandler<String, String> {
  private final Context context;

  public ItemUpdateEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    CaseInsensitiveMap<String, String> headers =
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));

    RequestRepository requestRepository = new RequestRepository(context, headers);
    InventoryStorageClient inventoryStorageClient = new InventoryStorageClient(
      context.owner(), headers, getLocationCache(), getServicePointCache());

    ItemUpdateProcessorForRequest itemUpdateProcessorForRequest =
      new ItemUpdateProcessorForRequest(requestRepository, inventoryStorageClient);

    return itemUpdateProcessorForRequest.run(kafkaConsumerRecord.key(), payload);
  }
}
