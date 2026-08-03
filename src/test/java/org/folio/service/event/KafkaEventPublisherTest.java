package org.folio.service.event;

import static io.vertx.core.Future.succeededFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.folio.kafka.KafkaProducerManager;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;

class KafkaEventPublisherTest {

  private static final String TENANT_ID = "test_tenant";
  private static final String TOPIC = "folio.test_tenant.circulation.LOG_RECORD";

  @SuppressWarnings("unchecked")
  private final KafkaProducerManager producerManager = mock(KafkaProducerManager.class);
  @SuppressWarnings({"unchecked", "rawtypes"})
  private final KafkaProducer producer = mock(KafkaProducer.class);

  @SuppressWarnings("unchecked")
  private KafkaEventPublisher<String, JsonObject> publisher;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    publisher = new KafkaEventPublisher<>(TOPIC, producerManager);
    when(producerManager.createShared(anyString())).thenReturn(producer);
    when(producer.send(any())).thenReturn(succeededFuture());
    when(producer.flush()).thenReturn(succeededFuture());
    when(producer.close()).thenReturn(succeededFuture());
  }

  @Test
  void publishSendsRecordAndReturnsSuccess() {
    Map<String, String> headers = Map.of(XOkapiHeaders.TENANT, TENANT_ID);
    DomainEvent<JsonObject> event = DomainEvent.<JsonObject>builder()
      .id(UUID.randomUUID())
      .type(DomainEventType.CREATED)
      .tenant(TENANT_ID)
      .timestamp(System.currentTimeMillis())
      .data(new JsonObject().put("key", "value"))
      .build();

    Future<Void> result = publisher.publish("some-key", event, headers);

    assertThat(result.succeeded(), is(true));
    verify(producerManager, times(1)).createShared(TOPIC);
    verify(producer, times(1)).send(any());
  }

  @Test
  void publishReturnsSuccessEvenWhenProducerThrows() {
    when(producerManager.createShared(anyString())).thenThrow(new RuntimeException("Kafka unavailable"));
    Map<String, String> headers = Map.of(XOkapiHeaders.TENANT, TENANT_ID);
    DomainEvent<JsonObject> event = DomainEvent.<JsonObject>builder()
      .id(UUID.randomUUID())
      .type(DomainEventType.CREATED)
      .tenant(TENANT_ID)
      .timestamp(System.currentTimeMillis())
      .data(new JsonObject())
      .build();

    Future<Void> result = publisher.publish("some-key", event, headers);

    assertThat(result.succeeded(), is(true));
  }

}
