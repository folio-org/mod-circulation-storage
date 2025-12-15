package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.service.event.EntityChangedEventPublisher;
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
public class RequestExpirationServiceTest {

  private static final String TENANT_ID = "test_tenant";

  private RequestExpirationService service;

  @Mock private Conn conn;
  @Mock private PostgresClient postgresClient;
  @Mock private EventPublisherService eventPublisherService;
  @Mock private EntityChangedEventPublisher<String, Request> eventPublisher;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new RequestExpirationService("itemId", Request::getItemId,
      postgresClient, eventPublisherService, eventPublisher);
  }

  @Test
  public void shouldDoRequestExpirationWithoutErrorIfEventFailedToSent(VertxTestContext context) {
    var id1 = UUID.randomUUID().toString();
    var id2 = UUID.randomUUID().toString();
    var id3 = UUID.randomUUID().toString();
    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);
    when(postgresClient.withTrans(any())).then(this::withTransHandler);

    var expiredRequestsRowSet = new LocalRowSet(3).withRows(List.of(
        getRequestRowSetMock(id1), getRequestRowSetMock(id2), getRequestRowSetMock(id3)));
    when(conn.execute(anyString())).thenReturn(succeededFuture(expiredRequestsRowSet));
    when(conn.update(anyString(), any(), any())).thenReturn(succeededFuture(new LocalRowSet(0)));
    when(eventPublisherService.publishLogRecord(any(), any())).thenReturn(succeededFuture());
    when(eventPublisher.publishUpdated(eq(id1), any(), any())).thenReturn(succeededFuture());
    when(eventPublisher.publishUpdated(eq(id2), any(), any())).thenReturn(
      failedFuture(new NoStackTraceThrowable("Event publishing failed: " + id2)));
    when(eventPublisher.publishUpdated(eq(id3), any(), any())).thenReturn(succeededFuture());

    service.doRequestExpiration().onComplete(ar -> {
      context.verify(() -> {
        Assertions.assertNull(ar.result());
        verify(eventPublisher, times(3)).publishUpdated(any(), any(), any());
      });
      context.completeNow();
    });
  }

  private Future<?> withTransHandler(InvocationOnMock inv) {
    var handler = inv.<Function<Conn, Future<?>>>getArgument(0);
    return handler.apply(conn);
  }

  private static JsonObject getRequestJson(String id) {
    return new JsonObject()
      .put("id", id)
      .put("itemId", "2baee507-b0c3-4dc9-b571-cd29d2c9e9e6")
      .put("requesterId", "83b1a7e9-7bb5-47d1-9c39-336545f17e80")
      .put("status", "Open - Awaiting pickup")
      .put("metadata", new JsonObject()
        .put("createdDate", "2023-10-01T10:00:00Z")
        .put("createdByUserId", "83b1a7e9-7bb5-47d1-9c39-336545f17e80")
        .put("createdByUsername", "test_user"));
  }

  private static Row getRequestRowSetMock(String id) {
    var rowMock = Mockito.mock(Row.class);
    when(rowMock.getColumnIndex("jsonb")).thenReturn(1);
    when(rowMock.get(JsonObject.class, 1)).thenReturn(getRequestJson(id));

    return rowMock;
  }
}
