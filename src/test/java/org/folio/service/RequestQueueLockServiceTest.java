package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.service.RequestQueueLockService.INSTANCE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

@ExtendWith(VertxExtension.class)
class RequestQueueLockServiceTest {
  private static final String TENANT_ID = "test_tenant";
  private static final String QUEUE_ID = "0f9f1b90-1f04-41ba-bb3c-76d2659cf80b";

  @Mock private PostgresClient postgresClient;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void tryAcquireReturnsLock() throws Exception {
    UUID lockId = UUID.randomUUID();
    RowSet<Row> acquiredLock = lockRows(lockId);
    when(postgresClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(succeededFuture(acquiredLock));

    Optional<RequestQueueLockService.Lock> result = new RequestQueueLockService(
      postgresClient, mock(Vertx.class)).tryAcquire(INSTANCE, QUEUE_ID, 1_000)
      .toCompletionStage().toCompletableFuture().get(1, SECONDS);

    assertThat(result.isPresent(), is(true));
    assertThat(result.get().id(), is(lockId));
    assertThat(result.get().queueType(), is(INSTANCE));
    assertThat(result.get().queueId().toString(), is(QUEUE_ID));
    assertThat(result.get().createdAt(), notNullValue());
    assertThat(result.get().expiresAt(), notNullValue());
  }

  @Test
  void tryAcquireReturnsEmptyWhenQueueIsAlreadyLocked() throws Exception {
    when(postgresClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(succeededFuture(emptyRows()));

    Optional<RequestQueueLockService.Lock> result = new RequestQueueLockService(
      postgresClient, mock(Vertx.class)).tryAcquire(INSTANCE, QUEUE_ID, 1_000)
      .toCompletionStage().toCompletableFuture().get(1, SECONDS);

    assertThat(result, is(Optional.empty()));
  }

  @Test
  void executeRetriesContentionAndReleasesLock(Vertx vertx) throws Exception {
    RowSet<Row> acquiredLock = lockRows(UUID.randomUUID());
    when(postgresClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(succeededFuture(emptyRows()))
      .thenReturn(succeededFuture(acquiredLock))
      .thenReturn(succeededFuture(emptyRows()));
    RequestQueueLockService service = new RequestQueueLockService(
      postgresClient, vertx, 1_000, 1, 100);

    String result = service.execute(INSTANCE, QUEUE_ID, () -> succeededFuture("done"))
      .toCompletionStage().toCompletableFuture().get(1, SECONDS);

    assertThat(result, is("done"));
    verify(postgresClient, times(3)).execute(anyString(), any(Tuple.class));
  }

  @Test
  void executeReleasesLockAndPreservesOperationFailure(Vertx vertx) {
    RowSet<Row> acquiredLock = lockRows(UUID.randomUUID());
    when(postgresClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(succeededFuture(acquiredLock))
      .thenReturn(succeededFuture(emptyRows()));
    RequestQueueLockService service = new RequestQueueLockService(
      postgresClient, vertx, 1_000, 1, 100);

    ExecutionException exception = assertThrows(ExecutionException.class,
      () -> service.execute(INSTANCE, QUEUE_ID, () -> failedFuture("operation failed"))
        .toCompletionStage().toCompletableFuture().get(1, SECONDS));

    assertThat(exception.getCause().getMessage(), containsString("operation failed"));
    verify(postgresClient, times(2)).execute(anyString(), any(Tuple.class));
  }

  @Test
  void executePreservesSuccessWhenReleaseFails(Vertx vertx) throws Exception {
    RowSet<Row> acquiredLock = lockRows(UUID.randomUUID());
    when(postgresClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(succeededFuture(acquiredLock))
      .thenReturn(failedFuture("release failed"));
    RequestQueueLockService service = new RequestQueueLockService(
      postgresClient, vertx, 1_000, 1, 100);

    String result = service.execute(INSTANCE, QUEUE_ID, () -> succeededFuture("done"))
      .toCompletionStage().toCompletableFuture().get(1, SECONDS);

    assertThat(result, is("done"));
  }

  @Test
  void executeFailsAfterMaximumWait(Vertx vertx) {
    when(postgresClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(succeededFuture(emptyRows()));
    RequestQueueLockService service = new RequestQueueLockService(
      postgresClient, vertx, 1_000, 1, 5);

    ExecutionException exception = assertThrows(ExecutionException.class,
      () -> service.execute(INSTANCE, QUEUE_ID, () -> succeededFuture("not called"))
        .toCompletionStage().toCompletableFuture().get(1, SECONDS));

    assertThat(exception.getCause().getMessage(), containsString("Timed out acquiring"));
    verify(postgresClient, atLeast(1)).execute(anyString(), any(Tuple.class));
  }

  private static RowSet<Row> emptyRows() {
    return new LocalRowSet(0);
  }

  private static RowSet<Row> lockRows(UUID lockId) {
    OffsetDateTime createdAt = OffsetDateTime.now();
    Row row = mock(Row.class);
    when(row.getUUID("id")).thenReturn(lockId);
    when(row.getString("queue_type")).thenReturn(INSTANCE);
    when(row.getUUID("queue_id")).thenReturn(UUID.fromString(QUEUE_ID));
    when(row.getOffsetDateTime("created_at")).thenReturn(createdAt);
    when(row.getOffsetDateTime("expires_at")).thenReturn(createdAt.plusSeconds(1));
    return new LocalRowSet(1).withRows(List.of(row));
  }
}
