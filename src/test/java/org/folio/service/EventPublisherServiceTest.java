package org.folio.service;

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
import java.util.concurrent.CompletableFuture;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.service.event.LogRecordEventPublisher;
import org.folio.support.exception.LogEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class EventPublisherServiceTest {

  private static final String TENANT_ID = "test_tenant";
  private static final Map<String, String> HEADERS = Map.of(XOkapiHeaders.TENANT, TENANT_ID);

  private PubSubPublishingService pubSubPublishingService;
  private LogRecordEventPublisher logRecordEventPublisher;
  private EventPublisherService service;

  @BeforeEach
  void setUp() {
    pubSubPublishingService = mock(PubSubPublishingService.class);
    logRecordEventPublisher = mock(LogRecordEventPublisher.class);

    when(pubSubPublishingService.publishEvent(anyString(), anyString()))
      .thenReturn(CompletableFuture.completedFuture(true));
    when(logRecordEventPublisher.publish(any(), any(), any())).thenReturn(succeededFuture());

    service = new EventPublisherService(pubSubPublishingService, logRecordEventPublisher, HEADERS);
  }

  @Test
  void publishLogRecordPublishesToBothKafkaAndPubSub() {
    JsonObject payload = new JsonObject().put("requestId", "req-1");

    Future<Void> result = service.publishLogRecord(payload, LogEventType.REQUEST_EXPIRED);

    assertThat(result.succeeded(), is(true));
    verify(logRecordEventPublisher, times(1))
      .publish(eq(payload), eq(LogEventType.REQUEST_EXPIRED), eq(HEADERS));
    verify(pubSubPublishingService, times(1)).publishEvent(eq("LOG_RECORD"), anyString());
  }

  @Test
  void publishLogRecordAlwaysPublishesToKafkaRegardlessOfPubSub() {
    when(pubSubPublishingService.publishEvent(anyString(), anyString()))
      .thenReturn(CompletableFuture.failedFuture(new RuntimeException("PubSub down")));

    service.publishLogRecord(new JsonObject(), LogEventType.REQUEST_EXPIRED);

    verify(logRecordEventPublisher, times(1)).publish(any(), any(), any());
  }

}
