package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestPolicyRepository;
import org.folio.persist.RequestRepository;
import org.folio.rest.client.InventoryStorageClient;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.service.event.handler.processor.ItemRetrievalServicePointUpdateProcessorForRequest;
import org.folio.service.event.handler.processor.ServicePointUpdateProcessorForRequest;
import org.folio.service.event.handler.processor.ServicePointUpdateProcessorForRequestPolicy;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ServicePointUpdateEventHandler implements AsyncRecordHandler<String, String> {
  private static final Logger log = LogManager.getLogger();

  private final Context context;

  public ServicePointUpdateEventHandler(Context context) {
    this.context = context;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    JsonObject payload = new JsonObject(kafkaConsumerRecord.value());
    var headers = new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));
    String tenantId = headers.get(OKAPI_HEADER_TENANT);

    var requestRepository = new RequestRepository(context, headers);
    var requestPolicyRepository = new RequestPolicyRepository(context, headers);

    return new ServicePointUpdateProcessorForRequest(requestRepository)
        .run(kafkaConsumerRecord.key(), payload)
      .compose(notUsed -> new ServicePointUpdateProcessorForRequestPolicy(requestPolicyRepository)
        .run(kafkaConsumerRecord.key(), payload))
      .compose(notUsed -> new ItemRetrievalServicePointUpdateProcessorForRequest(requestRepository)
        .run(kafkaConsumerRecord.key(), payload))
      .onComplete(notUsed -> {
        // Update service point cache
        JsonObject newObject = payload.getJsonObject("new");
        if (newObject != null && newObject.containsKey("id")) {
          log.info("handle:: updating service point cache for tenantId: {}, servicePointId: {}",
            tenantId, newObject.getString("id"));
          InventoryStorageClient.updateServicePointCache(tenantId, newObject.getString("id"),
            newObject.mapTo(Servicepoint.class));
        }
      });
  }
}
