package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.impl.future.SucceededFuture;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.CheckoutLock;
import org.folio.rest.jaxrs.model.CheckoutLockRequest;
import org.folio.rest.jaxrs.resource.CheckOutLockStorage;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.UuidUtil;

import javax.ws.rs.core.Response;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.support.DbUtil.rowSetToStream;

public class CheckOutLockAPI implements CheckOutLockStorage {

  private static final String CHECK_OUT_LOCK_TABLE = "check_out_lock";

  private static final Logger log = LogManager.getLogger();

  @Override
  public void postCheckOutLockStorage(CheckoutLockRequest entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.debug("postCheckOutLockStorage:: entity {} {} ", entity.getUserId(), entity.getTtlMs());
    PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    postgresClient.execute(deleteOutdatedLockSql(entity.getUserId(), tenantId, entity.getTtlMs()), handler1 -> {
      try {
        if (handler1.succeeded()) {
          log.info("postCheckOutLockStorage:: creating lock for the userId {} ", entity.getUserId());
          postgresClient.execute(insertSql(entity.getUserId(), tenantId), handler2 -> {
            try {
              if (handler2.succeeded()) {
                log.info("postCheckOutLockStorage:: New lock is created for the userId {} ", entity.getUserId());
                asyncResultHandler.handle(succeededFuture(PostCheckOutLockStorageResponse.respond201WithApplicationJson(this.mapToCheckOutLock(handler2.result()))));
              } else {
                log.info("postCheckOutLockStorage:: Unable to create a lock ", handler2.cause());
                respondWith503Error(asyncResultHandler);
              }
            } catch (Exception ex) {
              log.warn("postCheckOutLockStorage:: Exception caught while creating lock ", ex);
              asyncResultHandler.handle(succeededFuture(PostCheckOutLockStorageResponse.respond500WithTextPlain(ex.getMessage())));
            }
          });
        } else {
          log.warn("postCheckOutLockStorage:: Deleting outdated lock is failed ",handler1.cause());
          respondWith503Error(asyncResultHandler);
        }
      } catch (Exception ex) {
        log.warn("postCheckOutLockStorage:: Exception caught while deleting lock ", ex);
        asyncResultHandler.handle(succeededFuture(PostCheckOutLockStorageResponse.respond500WithTextPlain(ex.getMessage())));
      }
    });
  }

  private void respondWith503Error(Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(succeededFuture(PostCheckOutLockStorageResponse.respond503WithTextPlain("Unable to acquire lock")));
  }

  @Override
  public void deleteCheckOutLockStorageByLockId(String lockId, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.debug("deleteCheckOutLockStorageByLockId:: deleting lock with lockId {} ", lockId);
    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    if (!UuidUtil.isUuid(lockId)) {
      asyncResultHandler.handle(new SucceededFuture<>(PostCheckOutLockStorageResponse.respond400WithTextPlain("Invalid lock id")));
      return;
    }
    postgresClient.execute(deleteLockByIdSql(lockId, tenantId), handler -> {
      if (handler.succeeded()) {
        asyncResultHandler.handle(succeededFuture(DeleteCheckOutLockStorageByLockIdResponse.respond204()));
      } else {
        asyncResultHandler.handle(succeededFuture(DeleteCheckOutLockStorageByLockIdResponse.respond500WithTextPlain(handler.cause())));
      }
    });
  }

  private String deleteLockByIdSql(String lockId, String tenantId) {
    return "delete from " + getTableName(tenantId) + " where id = '" + lockId + "'";
  }

  private String insertSql(String userId, String tenantId) {
    return "Insert into " + getTableName(tenantId) + "(id, user_id) values ('"+ UUID.randomUUID() + "','" + userId + "') returning id,user_id,creation_date";
  }

  private String deleteOutdatedLockSql(String userId, String tenantId, int ttlMs) {
    return "delete from " + getTableName(tenantId) + " where user_id = '" + userId + "' and creation_date + interval '" + ttlMs + " milliseconds' < current_timestamp";
  }

  private String getTableName(String tenantId) {
    return String.format("%s.%s", convertToPsqlStandard(tenantId), CHECK_OUT_LOCK_TABLE);
  }

  private CheckoutLock mapToCheckOutLock(RowSet<Row> rowSet) {
    log.debug("mapToCheckOutLock:: rowSet {} ", rowSet.size());
    if (rowSet.size() == 0) {
      return null;
    }
    return rowSetToStream(rowSet).map(row -> {
        log.info(row.getLocalDateTime("creation_date"));
        return new CheckoutLock()
          .withId(row.getUUID("id").toString())
          .withUserId(row.getUUID("user_id").toString())
          .withCreationDate(Date.from(row.getLocalDateTime("creation_date").atZone(ZoneId.systemDefault()).toInstant()));
      })
      .collect(Collectors.toList()).get(0);
  }

}
