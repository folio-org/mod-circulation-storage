package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestPolicyRepository;
import org.folio.persist.RequestRepository;
import org.folio.service.event.handler.processor.ItemRetrievalServicePointUpdateProcessorForRequest;
import org.folio.service.event.handler.processor.ServicePointUpdateProcessorForRequest;
import org.folio.service.event.handler.processor.ServicePointUpdateProcessorForRequestPolicy;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ServicePointUpdateEventHandler extends BaseInventoryEventHandler implements AsyncRecordHandler<String, String> {
  private final Context context;

  public ServicePointUpdateEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    var headers = new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));

    // Invalidate cache for updated service point
    JsonObject newObject = payload.getJsonObject("new");
    if (newObject != null && newObject.containsKey("id")) {
      String servicePointId = newObject.getString("id");
      invalidateServicePointCache(servicePointId);
    }

    var requestRepository = new RequestRepository(context, headers,
      getLocationCache(), getServicePointCache());
    var requestPolicyRepository = new RequestPolicyRepository(context, headers);

    return new ServicePointUpdateProcessorForRequest(requestRepository)
        .run(kafkaConsumerRecord.key(), payload)
      .compose(notUsed -> new ServicePointUpdateProcessorForRequestPolicy(requestPolicyRepository)
        .run(kafkaConsumerRecord.key(), payload))
      .compose(notUsed -> new ItemRetrievalServicePointUpdateProcessorForRequest(requestRepository)
        .run(kafkaConsumerRecord.key(), payload));
  }
}
