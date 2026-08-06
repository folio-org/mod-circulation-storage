package org.folio.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.support.exception.LogEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class EventPublisherServiceTest {

  private static final String TENANT_ID = "test_tenant";
  private static final Map<String, String> HEADERS = Map.of(XOkapiHeaders.TENANT, TENANT_ID);

  private PubSubPublishingService pubSubPublishingService;
  private EventPublisherService service;

  @BeforeEach
  void setUp() {
    pubSubPublishingService = mock(PubSubPublishingService.class);

    when(pubSubPublishingService.publishEvent(anyString(), anyString()))
      .thenReturn(CompletableFuture.completedFuture(true));

    service = new EventPublisherService(pubSubPublishingService);
  }

  @Test
  void publishLogRecordPublishesToPubSub() {
    JsonObject payload = new JsonObject().put("requestId", "req-1");

    Future<Void> result = service.publishLogRecord(payload, LogEventType.REQUEST_EXPIRED);

    assertThat(result.succeeded(), is(true));
    verify(pubSubPublishingService, times(1)).publishEvent(eq("LOG_RECORD"), anyString());
  }

}
