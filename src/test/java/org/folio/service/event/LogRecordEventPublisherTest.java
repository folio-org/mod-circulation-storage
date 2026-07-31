package org.folio.service.event;

import static io.vertx.core.Future.succeededFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.support.LogEventPayloadField;
import org.folio.support.exception.LogEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class LogRecordEventPublisherTest {

  private static final String TENANT_ID = "test_tenant";

  @SuppressWarnings("unchecked")
  private final KafkaEventPublisher<String, JsonObject> kafkaEventPublisher = mock(KafkaEventPublisher.class);

  private LogRecordEventPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new LogRecordEventPublisher(kafkaEventPublisher);
    when(kafkaEventPublisher.publish(anyString(), any(), any())).thenReturn(succeededFuture());
  }

  @Test
  void publishesLogRecordEventWithExpectedPayload() {
    Map<String, String> okapiHeaders = Map.of(XOkapiHeaders.TENANT, TENANT_ID);
    JsonObject payload = new JsonObject().put("requestId", "request-id");

    Future<Void> result = publisher.publish(payload, LogEventType.REQUEST_EXPIRED, okapiHeaders);

    assertThat(result.succeeded(), is(true));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<DomainEvent<JsonObject>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(kafkaEventPublisher, times(1))
      .publish(anyString(), eventCaptor.capture(), eq(okapiHeaders));

    DomainEvent<JsonObject> publishedEvent = eventCaptor.getValue();
    assertThat(publishedEvent.getTenant(), is(TENANT_ID));
    assertThat(publishedEvent.getType(), is(DomainEventType.CREATED));

    JsonObject publishedContext = publishedEvent.getData();
    assertThat(publishedContext.getJsonObject(LogEventPayloadField.PAYLOAD.value()), is(payload));
    assertThat(publishedContext.getString(LogEventPayloadField.LOG_EVENT_TYPE.value()),
      is(LogEventType.REQUEST_EXPIRED.value()));
  }

}
