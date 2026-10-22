package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/** Coordinates storage-owned request queue position updates with mod-circulation. */
public class RequestQueueLockService {
  private static final Logger log = LogManager.getLogger(RequestQueueLockService.class);
  public static final String INSTANCE = "INSTANCE";
  public static final String ITEM = "ITEM";
  private static final String TABLE_NAME = "request_queue_lock";
  private static final long DEFAULT_TTL_MS = 30_000;
  private static final long RETRY_INTERVAL_MS = 50;
  private static final long MAX_WAIT_MS = 10_000;

  private final PostgresClient pgClient;
  private final Vertx vertx;
  private final long ttlMs;
  private final long retryIntervalMs;
  private final long maxWaitMs;

  public RequestQueueLockService(PostgresClient pgClient, Vertx vertx) {
    this(pgClient, vertx, DEFAULT_TTL_MS, RETRY_INTERVAL_MS, MAX_WAIT_MS);
  }

  RequestQueueLockService(PostgresClient pgClient, Vertx vertx, long ttlMs,
    long retryIntervalMs, long maxWaitMs) {
    this.pgClient = pgClient;
    this.vertx = vertx;
    this.ttlMs = ttlMs;
    this.retryIntervalMs = retryIntervalMs;
    this.maxWaitMs = maxWaitMs;
  }

  public <T> Future<T> execute(String queueType, String queueId,
    Supplier<Future<T>> criticalSection) {

    long startedAt = System.nanoTime();
    return acquireWithRetry(queueType, queueId, ttlMs, startedAt)
      .compose(lock -> executeAndRelease(lock, criticalSection));
  }

  public Future<Optional<Lock>> tryAcquire(String queueType, String queueId, long ttlMs) {
    UUID lockId = UUID.randomUUID();
    Tuple parameters = Tuple.of(lockId, queueType, UUID.fromString(queueId), ttlMs);

    return pgClient.execute(acquireSql(pgClient.getTenantId()), parameters)
      .map(rows -> rows.size() == 0
        ? Optional.empty()
        : Optional.of(mapLock(rows.iterator().next())));
  }

  public Future<Void> release(UUID lockId) {
    return pgClient.execute(deleteSql(pgClient.getTenantId()), Tuple.of(lockId)).mapEmpty();
  }

  private Future<Lock> acquireWithRetry(String queueType, String queueId, long ttlMs,
    long startedAt) {

    return tryAcquire(queueType, queueId, ttlMs)
      .compose(lock -> {
        if (lock.isPresent()) {
          return succeededFuture(lock.get());
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        if (elapsedMs + retryIntervalMs > maxWaitMs) {
          return failedFuture("Timed out acquiring request queue lock for "
            + queueType + ":" + queueId);
        }

        Promise<Lock> retry = Promise.promise();
        vertx.setTimer(retryIntervalMs, ignored -> acquireWithRetry(
          queueType, queueId, ttlMs, startedAt).onComplete(retry));
        return retry.future();
      });
  }

  private <T> Future<T> executeAndRelease(Lock lock,
    Supplier<Future<T>> criticalSection) {

    Future<T> operation;
    try {
      operation = criticalSection.get();
    } catch (Exception exception) {
      operation = failedFuture(exception);
    }

    return operation.transform(operationResult -> releaseSafely(lock)
      .transform(releaseResult -> {
        if (releaseResult.failed()) {
          log.error("Failed to release request queue lock {} for {}:{}",
            lock.id(), lock.queueType(), lock.queueId(), releaseResult.cause());
        }
        if (operationResult.succeeded()) {
          return succeededFuture(operationResult.result());
        }
        return failedFuture(operationResult.cause());
      }));
  }

  private Future<Void> releaseSafely(Lock lock) {
    try {
      return release(lock.id());
    } catch (Exception exception) {
      return failedFuture(exception);
    }
  }

  private Lock mapLock(Row row) {
    return new Lock(row.getUUID("id"), row.getString("queue_type"),
      row.getUUID("queue_id"), row.getOffsetDateTime("created_at"),
      row.getOffsetDateTime("expires_at"));
  }

  private String acquireSql(String tenantId) {
    String table = convertToPsqlStandard(tenantId) + "." + TABLE_NAME;
    return "INSERT INTO " + table + " (id, queue_type, queue_id, created_at, expires_at) "
      + "VALUES ($1, $2, $3, CURRENT_TIMESTAMP, "
      + "CURRENT_TIMESTAMP + ($4::bigint * INTERVAL '1 millisecond')) "
      + "ON CONFLICT (queue_type, queue_id) DO UPDATE SET "
      + "id = EXCLUDED.id, created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at "
      + "WHERE " + TABLE_NAME + ".expires_at <= CURRENT_TIMESTAMP "
      + "RETURNING id, queue_type, queue_id, created_at, expires_at";
  }

  private String deleteSql(String tenantId) {
    return "DELETE FROM " + convertToPsqlStandard(tenantId) + "." + TABLE_NAME
      + " WHERE id = $1";
  }

  public record Lock(UUID id, String queueType, UUID queueId,
                     OffsetDateTime createdAt, OffsetDateTime expiresAt) { }
}
