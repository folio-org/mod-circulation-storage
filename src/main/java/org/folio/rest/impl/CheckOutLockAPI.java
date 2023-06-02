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
import org.folio.support.PgClientFutureAdapter;
import org.folio.util.UuidUtil;

import javax.ws.rs.core.Response;
import java.sql.Date;
import java.util.Map;
import java.util.stream.Collectors;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.support.DbUtil.rowSetToStream;

public class CheckOutLockAPI implements CheckOutLockStorage {

  private static final String CHECK_OUT_LOCK_TABLE = "check_out_lock";

  private static final Logger log = LogManager.getLogger();

  @Override
  public void postCheckOutLockStorage(CheckoutLockRequest entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.info("postCheckOutLockStorage:: entity {} {} ", entity.getUserId(), entity.getTtlMs());
    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    if (!UuidUtil.isUuid(entity.getUserId())) {
      asyncResultHandler.handle(new SucceededFuture<>(PostCheckOutLockStorageResponse.respond400WithTextPlain("Invalid UserId")));
      return;
    }
    PgClientFutureAdapter pgClient = PgClientFutureAdapter.create(vertxContext, okapiHeaders);
    pgClient.execute(deleteAndInsertSql(entity.getUserId(), tenantId, entity.getTtlMs()))
      .onSuccess(rows -> PostCheckOutLockStorageResponse.respond201WithApplicationJson(this.mapToCheckOutLock(rows)))
      .onFailure(err -> {
        log.info("Inside on failure ",err);
        PostCheckOutLockStorageResponse.respond503WithTextPlain("Unable to acquire lock");
      })
      .map(x -> {
        log.info("Inside map {} ",x);
        return (Response) x;
      })
      .onComplete(asyncResultHandler);
  }

  @Override
  public void deleteCheckOutLockStorageByLockId(String lockId, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    if (!UuidUtil.isUuid(lockId)) {
      asyncResultHandler.handle(new SucceededFuture<>(PostCheckOutLockStorageResponse.respond400WithTextPlain("Invalid UserId")));
      return;
    }
    PgClientFutureAdapter pgClient = PgClientFutureAdapter.create(vertxContext, okapiHeaders);
    pgClient.execute(deleteLockByIdSql(lockId, tenantId))
      .onSuccess(rows -> DeleteCheckOutLockStorageByLockIdResponse.respond204())
      .onFailure(err -> DeleteCheckOutLockStorageByLockIdResponse.respond500WithTextPlain("Internal server error"))
      .map(Response.class::cast)
      .onComplete(asyncResultHandler);
  }

  private String deleteLockByIdSql(String lockId, String tenantId) {
    String tableName = String.format("%s.%s", convertToPsqlStandard(tenantId), CHECK_OUT_LOCK_TABLE);
    return "delete from " + tableName + "where id = '" + lockId + "'";
  }

  private String deleteAndInsertSql(String userId, String tenantId, int ttlMs) {
    String tableName = String.format("%s.%s", convertToPsqlStandard(tenantId), CHECK_OUT_LOCK_TABLE);
    String sql = "delete from " + tableName + " where user_id = '" + userId + "' and creation_date + interval '" + ttlMs +
      "' milliseconds < current_timestamp;Insert into " + tableName + "(user_id) values ('" + userId + "')returning id, creation_date";
    log.info("sql {} ",sql);
    return sql;
  }

  private CheckoutLock mapToCheckOutLock(RowSet<Row> rowSet) {
    log.info("Inside mapToCheckOutLock {} ",rowSet.size());
    if (rowSet.size() == 0)
      return null;
    return rowSetToStream(rowSet).map(row -> new CheckoutLock()
        .withId(row.getUUID("id").toString())
        .withUserId(row.getUUID("userid").toString())
        .withCreationDate(Date.valueOf(row.getLocalDate("creation_date"))))
      .collect(Collectors.toList()).get(0);
  }

}
