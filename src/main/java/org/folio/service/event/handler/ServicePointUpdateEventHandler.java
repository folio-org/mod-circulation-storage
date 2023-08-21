package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestPolicyRepository;
import org.folio.persist.RequestRepository;
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

    var headers = kafkaHeadersToMap(kafkaConsumerRecord.headers());
    var requestRepository = new RequestRepository(context, headers);
    var requestPolicyRepository = new RequestPolicyRepository(context, headers);

    return new ServicePointUpdateProcessorForRequest(requestRepository)
        .run(kafkaConsumerRecord.key(), new CaseInsensitiveMap<>(headers), payload)
      .compose(notUsed -> new ServicePointUpdateProcessorForRequestPolicy(requestPolicyRepository)
        .run(kafkaConsumerRecord.key(), new CaseInsensitiveMap<>(headers), payload));
  }
}
