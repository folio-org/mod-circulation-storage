package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.service.event.handler.processor.ServicePointUpdateProcessorForRequest;
import org.folio.service.event.handler.processor.ServicePointUpdateProcessorForRequestPolicy;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ServicePointUpdateEventHandler implements AsyncRecordHandler<String, String> {
  private final Context context;

  public ServicePointUpdateEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {

    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());

    ServicePointUpdateProcessorForRequest servicePointUpdateProcessorForRequest =
      new ServicePointUpdateProcessorForRequest(context);
    ServicePointUpdateProcessorForRequestPolicy servicePointUpdateProcessorForRequestPolicy =
      new ServicePointUpdateProcessorForRequestPolicy(context);

    return servicePointUpdateProcessorForRequest.run(kafkaConsumerRecord.key(),
        new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers())), payload)
      .compose(notUsed -> servicePointUpdateProcessorForRequestPolicy.run(
        kafkaConsumerRecord.key(), new CaseInsensitiveMap<>(kafkaHeadersToMap(
          kafkaConsumerRecord.headers())), payload));
  }
}
