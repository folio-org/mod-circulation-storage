package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.STRICT_STUBS;

import io.vertx.core.impl.NoStackTraceException;
import io.vertx.core.json.Json;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.service.event.EntityChangedEventPublisher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;

@RunWith(VertxUnitRunner.class)
public class RequestExpirationServiceTest {

  private static final String TENANT_ID = "test_tenant";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(STRICT_STUBS);
  private RequestExpirationService service;

  @Mock private Conn conn;
  @Mock private PostgresClient postgresClient;
  @Mock private EventPublisherService eventPublisherService;
  @Mock private EntityChangedEventPublisher<String, Request> eventPublisher;

  @Before
  public void setUp() {
    service = new RequestExpirationService("itemId", Request::getItemId,
      postgresClient, eventPublisherService, eventPublisher);
  }

  @Test
  public void shouldDoRequestExpirationWithoutErrorIfEventFailedToSent(TestContext context) {
    var id1 = UUID.randomUUID().toString();
    var id2 = UUID.randomUUID().toString();
    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);
    when(postgresClient.withTrans(any())).then(this::withTransHandler);

    var expiredRequestsRowSet = new LocalRowSet(2)
      .withRows(List.of(getRequestRowSetMock(id1), getRequestRowSetMock(id2)));
    when(conn.execute(anyString())).thenReturn(succeededFuture(expiredRequestsRowSet));
    when(conn.update(anyString(), any(), any())).thenReturn(succeededFuture(new LocalRowSet(0)));
    when(eventPublisher.publishUpdated(eq(id1), any(), any())).thenReturn(succeededFuture());
    when(eventPublisher.publishUpdated(eq(id2), any(), any())).thenReturn(
      failedFuture(new NoStackTraceException("Event publishing failed: " + id2)));

    service.doRequestExpiration().onComplete(context.asyncAssertSuccess(context::assertNull));
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
