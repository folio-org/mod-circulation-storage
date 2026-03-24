package org.folio.service.event.handler;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.RequestPolicyRepository;
import org.folio.rest.client.InventoryStorageClient;
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
    String tenantId = headers.get(OKAPI_HEADER_TENANT);

    JsonObject oldObject = payload.getJsonObject("old");
    if (oldObject != null && oldObject.containsKey("id")) {
      InventoryStorageClient.invalidateServicePoint(tenantId, oldObject.getString("id"));
    }

    return new ServicePointDeleteProcessorForRequestPolicy(new RequestPolicyRepository(context, headers))
      .run(kafkaConsumerRecord.key(), payload);
  }
}
