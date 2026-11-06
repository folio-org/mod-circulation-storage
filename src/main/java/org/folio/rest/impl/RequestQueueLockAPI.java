package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.RequestQueueLock;
import org.folio.rest.jaxrs.model.RequestQueueLockRequest;
import org.folio.rest.jaxrs.resource.RequestQueueLockStorage;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.RequestQueueLockService;
import org.folio.util.UuidUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

/**
 * Stores short-lived, queue-scoped locks used while assigning request positions.
 */
public class RequestQueueLockAPI implements RequestQueueLockStorage {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Validate
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

    PostgresClient client = postgresClient(vertxContext, okapiHeaders);
    RequestQueueLockService lockService = new RequestQueueLockService(client,
      vertxContext.owner());

    lockService.tryAcquire(entity.getQueueType().value(), entity.getQueueId(),
      entity.getTtlMs()).onComplete(result -> {
      if (result.failed()) {
        log.error("Failed to acquire request queue lock for {}:{}",
          entity.getQueueType(), entity.getQueueId(), result.cause());
        asyncResultHandler.handle(succeededFuture(
          PostRequestQueueLockStorageResponse.respond500WithTextPlain(
            "Unable to acquire request queue lock")));
        return;
      }

      if (result.result().isEmpty()) {
        asyncResultHandler.handle(succeededFuture(
          PostRequestQueueLockStorageResponse.respond409WithTextPlain(
            "Request queue is already locked")));
        return;
      }

      asyncResultHandler.handle(succeededFuture(
        PostRequestQueueLockStorageResponse.respond201WithApplicationJson(
          mapLock(result.result().get()))));
    });
  }

  @Validate
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

    PostgresClient client = postgresClient(vertxContext, okapiHeaders);
    RequestQueueLockService lockService = new RequestQueueLockService(client,
      vertxContext.owner());
    lockService.release(UUID.fromString(lockId)).onComplete(result -> {
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

  @SuppressWarnings("java:S2143") // RMB models still expose date-time fields as java.util.Date.
  private RequestQueueLock mapLock(RequestQueueLockService.Lock lock) {
    return new RequestQueueLock()
      .withId(lock.id().toString())
      .withQueueType(RequestQueueLock.QueueType.fromValue(lock.queueType()))
      .withQueueId(lock.queueId().toString())
      .withCreatedAt(java.util.Date.from(lock.createdAt().toInstant()))
      .withExpiresAt(java.util.Date.from(lock.expiresAt().toInstant()));
  }
}
