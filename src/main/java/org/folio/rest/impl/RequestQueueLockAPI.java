package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import java.lang.invoke.MethodHandles;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.RequestQueueLock;
import org.folio.rest.jaxrs.model.RequestQueueLockRequest;
import org.folio.rest.jaxrs.resource.RequestQueueLockStorage;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.UuidUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/**
 * Stores short-lived, queue-scoped locks used while assigning request positions.
 */
public class RequestQueueLockAPI implements RequestQueueLockStorage {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String TABLE_NAME = "request_queue_lock";

  @Override
  public void postRequestQueueLockStorage(RequestQueueLockRequest entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (!isValid(entity)) {
      asyncResultHandler.handle(succeededFuture(
        PostRequestQueueLockStorageResponse.respond400WithTextPlain(
          "queueType, a valid queueId, and a positive ttlMs are required")));
      return;
    }

    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    PostgresClient client = postgresClient(vertxContext, okapiHeaders);
    UUID lockId = UUID.randomUUID();
    Tuple parameters = Tuple.of(lockId, entity.getQueueType().value(),
      UUID.fromString(entity.getQueueId()), entity.getTtlMs());

    client.execute(acquireSql(tenantId), parameters, result -> {
      if (result.failed()) {
        log.error("Failed to acquire request queue lock for {}:{}",
          entity.getQueueType(), entity.getQueueId(), result.cause());
        asyncResultHandler.handle(succeededFuture(
          PostRequestQueueLockStorageResponse.respond500WithTextPlain(
            "Unable to acquire request queue lock")));
        return;
      }

      if (result.result().size() == 0) {
        asyncResultHandler.handle(succeededFuture(
          PostRequestQueueLockStorageResponse.respond409WithTextPlain(
            "Request queue is already locked")));
        return;
      }

      asyncResultHandler.handle(succeededFuture(
        PostRequestQueueLockStorageResponse.respond201WithApplicationJson(
          mapLock(result.result().iterator().next()))));
    });
  }

  @Override
  public void deleteRequestQueueLockStorageByLockId(String lockId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (!UuidUtil.isUuid(lockId)) {
      asyncResultHandler.handle(succeededFuture(
        DeleteRequestQueueLockStorageByLockIdResponse.respond400WithTextPlain(
          "Invalid lock id")));
      return;
    }

    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    PostgresClient client = postgresClient(vertxContext, okapiHeaders);
    client.execute(deleteSql(tenantId), Tuple.of(UUID.fromString(lockId)), result -> {
      if (result.failed()) {
        log.error("Failed to release request queue lock {}", lockId, result.cause());
        asyncResultHandler.handle(succeededFuture(
          DeleteRequestQueueLockStorageByLockIdResponse.respond500WithTextPlain(
            "Unable to release request queue lock")));
        return;
      }

      asyncResultHandler.handle(succeededFuture(
        DeleteRequestQueueLockStorageByLockIdResponse.respond204()));
    });
  }

  private boolean isValid(RequestQueueLockRequest entity) {
    return entity != null && entity.getQueueType() != null
      && entity.getQueueId() != null && UuidUtil.isUuid(entity.getQueueId())
      && entity.getTtlMs() != null && entity.getTtlMs() > 0;
  }

  private RequestQueueLock mapLock(Row row) {
    return new RequestQueueLock()
      .withId(row.getUUID("id").toString())
      .withQueueType(RequestQueueLock.QueueType.fromValue(row.getString("queue_type")))
      .withQueueId(row.getUUID("queue_id").toString())
      .withCreatedAt(Date.from(row.getOffsetDateTime("created_at").toInstant()))
      .withExpiresAt(Date.from(row.getOffsetDateTime("expires_at").toInstant()));
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
}
