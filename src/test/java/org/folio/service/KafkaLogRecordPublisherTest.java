package org.folio.service;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.folio.kafka.KafkaProducerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

class KafkaLogRecordPublisherTest {

  @SuppressWarnings("unchecked")
  private final KafkaProducer<String, String> producer = mock(KafkaProducer.class);
  @SuppressWarnings("unchecked")
  private final KafkaProducerManager producerManager = mock(KafkaProducerManager.class);

  private KafkaLogRecordPublisher publisher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.send(any())).thenReturn(Future.succeededFuture());
    when(producer.flush()).thenReturn(Future.succeededFuture());
    when(producer.close()).thenReturn(Future.succeededFuture());
    publisher = new KafkaLogRecordPublisher("folio.test_tenant.audit.LOG_RECORD", producerManager);
  }

  @Test
  void publishSendsToKafkaAndReturnsSucceededFuture() {
    var payload = new JsonObject()
        .put("logEventType", "REQUEST_EXPIRED_EVENT")
        .put("payload", new JsonObject().put("requests", new JsonObject()));
    var headers = Map.of(TENANT, "test_tenant", "x-okapi-url", "http://okapi:9130");

    Future<Void> result = publisher.publish("request-id-1", payload, headers);

    assertThat(result.succeeded(), is(true));
    verify(producer, times(1)).send(any(KafkaProducerRecord.class));
  }

  @Test
  void publishAlwaysReturnsSucceededFutureEvenWhenKafkaSendFails() {
    when(producer.send(any())).thenReturn(Future.failedFuture("broker down"));
    var headers = Map.of(TENANT, "test_tenant");

    Future<Void> result = publisher.publish("request-id-2", new JsonObject(), headers);

    assertThat(result.succeeded(), is(true));
  }
}
