package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.service.event.EntityChangedEventPublisher;
import org.folio.service.event.KafkaLogRecordPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Row;
import org.junit.jupiter.api.Assertions;

@ExtendWith({VertxExtension.class})
class RequestExpirationServiceTest {

  private static final String TENANT_ID = "test_tenant";

  private RequestExpirationService service;

  @Mock private Conn conn;
  @Mock private PostgresClient postgresClient;
  @Mock private KafkaLogRecordPublisher kafkaLogRecordPublisher;
  @Mock private EntityChangedEventPublisher<String, Request> eventPublisher;
  @Mock private RequestQueueLockService requestQueueLockService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new RequestExpirationService("itemId", Request::getItemId,
      postgresClient, kafkaLogRecordPublisher, eventPublisher, Map.of(),
      requestQueueLockService);
    doAnswer(invocation -> {
        Supplier<Future<Object>> criticalSection = invocation.getArgument(2);
        return criticalSection.get();
      })
      .when(requestQueueLockService).execute(anyString(), anyString(), any());
  }

  @Test
  void shouldDoRequestExpirationWithoutErrorIfEventFailedToSent(VertxTestContext context) {
    var id1 = UUID.randomUUID().toString();
    var id2 = UUID.randomUUID().toString();
    var id3 = UUID.randomUUID().toString();
    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);
    when(postgresClient.withConn(any())).then(this::withTransHandler);
    when(postgresClient.withTrans(any())).then(this::withTransHandler);

    var expiredRequestsRowSet = new LocalRowSet(3).withRows(List.of(
        getRequestRowSetMock(id1), getRequestRowSetMock(id2), getRequestRowSetMock(id3)));
    when(conn.execute(anyString())).thenReturn(succeededFuture(expiredRequestsRowSet));
    when(conn.update(anyString(), any(), any())).thenReturn(succeededFuture(new LocalRowSet(0)));
    when(kafkaLogRecordPublisher.publish(any(), any(), any())).thenReturn(succeededFuture());
    when(eventPublisher.publishUpdated(eq(id1), any(), any())).thenReturn(succeededFuture());
    when(eventPublisher.publishUpdated(eq(id2), any(), any())).thenReturn(
      failedFuture(new NoStackTraceThrowable("Event publishing failed: " + id2)));
    when(eventPublisher.publishUpdated(eq(id3), any(), any())).thenReturn(succeededFuture());

    service.doRequestExpiration().onComplete(ar -> {
      context.verify(() -> {
        Assertions.assertNull(ar.result());
        verify(requestQueueLockService).execute(eq(RequestQueueLockService.ITEM),
          eq("2baee507-b0c3-4dc9-b571-cd29d2c9e9e6"), any());
        verify(conn, times(4)).execute(anyString());
        verify(eventPublisher, times(3)).publishUpdated(any(), any(), any());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldContinueWithRemainingQueuesWhenOneQueueFails(VertxTestContext context) {
    String firstItemId = UUID.randomUUID().toString();
    String secondItemId = UUID.randomUUID().toString();
    String firstRequestId = UUID.randomUUID().toString();
    String secondRequestId = UUID.randomUUID().toString();
    AtomicInteger lockAttempts = new AtomicInteger();

    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);
    when(postgresClient.withConn(any())).then(this::withTransHandler);
    when(postgresClient.withTrans(any())).then(this::withTransHandler);
    var expiredRequests = new LocalRowSet(2).withRows(List.of(
      getRequestRowSetMock(firstRequestId, firstItemId),
      getRequestRowSetMock(secondRequestId, secondItemId)));
    when(conn.execute(anyString())).thenReturn(succeededFuture(expiredRequests));
    when(conn.update(anyString(), any(), any())).thenReturn(succeededFuture(new LocalRowSet(0)));
    when(kafkaLogRecordPublisher.publish(any(), any(), any())).thenReturn(succeededFuture());
    when(eventPublisher.publishUpdated(anyString(), any(), any())).thenReturn(succeededFuture());
    doAnswer(invocation -> {
        if (lockAttempts.getAndIncrement() == 0) {
          return failedFuture("lock timeout");
        }
        Supplier<Future<Object>> criticalSection = invocation.getArgument(2);
        return criticalSection.get();
      })
      .when(requestQueueLockService).execute(anyString(), anyString(), any());

    service.doRequestExpiration().onComplete(ar -> {
      context.verify(() -> {
        Assertions.assertTrue(ar.failed());
        verify(requestQueueLockService, times(2)).execute(
          eq(RequestQueueLockService.ITEM), anyString(), any());
        Assertions.assertEquals(2, lockAttempts.get());
      });
      context.completeNow();
    });
  }

  private Future<?> withTransHandler(InvocationOnMock inv) {
    var handler = inv.<Function<Conn, Future<?>>>getArgument(0);
    return handler.apply(conn);
  }

  private static JsonObject getRequestJson(String id, String itemId) {
    return new JsonObject()
      .put("id", id)
      .put("itemId", itemId)
      .put("requesterId", "83b1a7e9-7bb5-47d1-9c39-336545f17e80")
      .put("status", "Open - Awaiting pickup")
      .put("metadata", new JsonObject()
        .put("createdDate", "2023-10-01T10:00:00Z")
        .put("createdByUserId", "83b1a7e9-7bb5-47d1-9c39-336545f17e80")
        .put("createdByUsername", "test_user"));
  }

  private static Row getRequestRowSetMock(String id) {
    return getRequestRowSetMock(id, "2baee507-b0c3-4dc9-b571-cd29d2c9e9e6");
  }

  private static Row getRequestRowSetMock(String id, String itemId) {
    var rowMock = Mockito.mock(Row.class);
    when(rowMock.getColumnIndex("jsonb")).thenReturn(1);
    when(rowMock.get(JsonObject.class, 1)).thenReturn(getRequestJson(id, itemId));

    return rowMock;
  }
}
