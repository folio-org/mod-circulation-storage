package org.folio.service.event.handler;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestRepository;
import org.folio.rest.client.InventoryStorageClient;
import org.folio.service.event.EntityChangedEventPublisher;
import org.folio.service.event.handler.processor.ItemUpdateProcessorForRequest;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ItemUpdateEventHandler implements AsyncRecordHandler<String, String> {
  private final Context context;
  private static final Logger log = getLogger(ItemUpdateEventHandler.class);
  public ItemUpdateEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    CaseInsensitiveMap<String, String> headers =
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));
    log.info("ItemUpdateEventHandler :: handle() :: kafkaHeaders: {}", headers);
    ItemUpdateProcessorForRequest itemUpdateProcessorForRequest =
      new ItemUpdateProcessorForRequest(new RequestRepository(context, headers), new InventoryStorageClient(context.owner(), headers));

    return itemUpdateProcessorForRequest.run(kafkaConsumerRecord.key(), payload);
  }
}
