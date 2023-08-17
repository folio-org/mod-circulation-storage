package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ServicePointDeleteEventHandler implements AsyncRecordHandler<String, String> {
  private final Context context;

  public ServicePointDeleteEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {

    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());

    ServicePointDeleteProcessorForRequestPolicy servicePointDeleteProcessorForRequestPolicy =
      new ServicePointDeleteProcessorForRequestPolicy(context);

    return servicePointDeleteProcessorForRequestPolicy.run(kafkaConsumerRecord.key(),
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers())), payload);
  }
}
