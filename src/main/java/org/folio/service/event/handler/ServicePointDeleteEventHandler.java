package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestPolicyRepository;
import org.folio.service.event.handler.processor.ServicePointDeleteProcessorForRequestPolicy;

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
    CaseInsensitiveMap<String, String> headers =
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));

    ServicePointDeleteProcessorForRequestPolicy servicePointDeleteProcessorForRequestPolicy =
      new ServicePointDeleteProcessorForRequestPolicy(
        new RequestPolicyRepository(context, headers));

    return servicePointDeleteProcessorForRequestPolicy.run(kafkaConsumerRecord.key(), payload);
  }
}
