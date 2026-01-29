package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestRepository;
import org.folio.rest.client.InventoryStorageClient;
import org.folio.service.event.handler.processor.ItemUpdateProcessorForRequest;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ItemUpdateEventHandler implements AsyncRecordHandler<String, String> {

  private static final Logger log = LogManager.getLogger();
  private final Context context;
  private final long eventProcessingDelayMs;

  public ItemUpdateEventHandler(Context context) {
    this.context = context;
    this.eventProcessingDelayMs = 0;
  }

  public ItemUpdateEventHandler(Context context, long eventProcessingDelayMs) {
    this.context = context;
    this.eventProcessingDelayMs = eventProcessingDelayMs;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    String eventKey = kafkaConsumerRecord.key();
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    CaseInsensitiveMap<String, String> headers =
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));
    ItemUpdateProcessorForRequest itemUpdateProcessorForRequest =
      new ItemUpdateProcessorForRequest(new RequestRepository(context, headers), new InventoryStorageClient(context.owner(), headers));

    if (eventProcessingDelayMs == 0) {
      return itemUpdateProcessorForRequest.run(eventKey, payload);
    }

    Promise<String> delayedEventProcessing = Promise.promise();
    log.info("handle:: processing event {} with delay of {} ms", eventKey, eventProcessingDelayMs);
    context.owner().setTimer(eventProcessingDelayMs, timerId ->
      itemUpdateProcessorForRequest.run(eventKey, payload)
        .onSuccess(delayedEventProcessing::complete));
    return delayedEventProcessing.future();
  }

}
