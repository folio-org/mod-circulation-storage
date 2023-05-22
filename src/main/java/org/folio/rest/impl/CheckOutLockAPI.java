package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.CheckoutLock;
import org.folio.rest.jaxrs.resource.CheckOutLockStorage;
import org.folio.support.PgClientFutureAdapter;

import javax.ws.rs.core.Response;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.support.DbUtil.rowSetToStream;

public class CheckOutLockAPI implements CheckOutLockStorage {

  private static final String CHECK_OUT_LOCK_TABLE = "check_out_lock";

  private static final Logger log  = LogManager.getLogger();
  @Override
  public void postCheckOutLockStorage(CheckoutLock entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.info("postCheckOutLockStorage:: entity {}", entity);
    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    PgClientFutureAdapter pgClient = PgClientFutureAdapter.create(vertxContext, okapiHeaders);
    pgClient.select(selectCheckOutLocks(tenantId, CHECK_OUT_LOCK_TABLE, entity.getUserId()))
      .compose(rows -> {
        log.info("rows {} ", rows);
        if(rows.size()==0){
          log.info("No checkout locks present");
          return pgClient.execute(insertSql(entity))
            .compose(x -> Future.succeededFuture(PostCheckOutLockStorageResponse.respond201WithApplicationJson(this.mapToCheckOutLock(x).get(0))));
        }else{
          log.info("Unable to acquire lock");
          return Future.failedFuture("Unable to acquire lock");
        }
      }).onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(PostCheckOutLockStorageResponse::respond500WithTextPlain);
  }

  @Override
  public void getCheckOutLockStorage(int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.info("Inside getCheckOutLockStorage");
  }

  @Override
  public void getCheckOutLockStorageByLockId(String lockId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

  }

  @Override
  public void deleteCheckOutLockStorageByLockId(String lockId, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

  }

  private String selectCheckOutLocks(String tenant, String tableName, String userId) {
    String sql = String.format("select * from %s ", tableName);
    if(!StringUtils.isBlank(userId)){
      sql = sql + String.format("where userId = '%s'", userId);
    }
    return sql;
  }

  private String insertSql(CheckoutLock checkoutLock) {
    return String.format("Insert into %s (userid) values('%s')", CHECK_OUT_LOCK_TABLE, checkoutLock.getUserId());
  }

  private List<CheckoutLock> mapToCheckOutLock(RowSet<Row> rowSet) {
    List<CheckoutLock>  checkoutLocks = rowSetToStream(rowSet)
      .map(row -> new CheckoutLock()
      .withId(row.getString("id"))
      .withUserId(row.getString("userId"))
      .withCreationDate(Date.valueOf(row.getLocalDate("creationDate"))))
      .collect(Collectors.toList());
    log.info("mapToCheckOutLock:: checkoutLock userId{}", checkoutLocks.get(0).getUserId());
    return checkoutLocks;
  }

  private Response mapExceptionToResponse(Throwable t) {

    log.error(t.getMessage(), t);

    return Response.status(500)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity("Internal Server Error")
      .build();
  }
}