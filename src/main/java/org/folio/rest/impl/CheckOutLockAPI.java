package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.future.SucceededFuture;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.CheckoutLock;
import org.folio.rest.jaxrs.resource.CheckOutLockStorage;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.client.exceptions.ResponseException;
import org.folio.support.PgClientFutureAdapter;

import javax.ws.rs.core.Response;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.persist.PgUtil.postgresClient;
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
        CheckoutLock lock = this.mapToCheckOutLock(rows);
        if(lock == null ){
          log.info("No checkout locks present");
          return pgClient.execute(insertSql(entity))
            .map(x -> PostCheckOutLockStorageResponse.respond201WithApplicationJson(entity))
            .map(Response.class::cast);
        }else{
          log.info("Unable to acquire lock");
          return Future.failedFuture("Unable to acquire lock");
        }
      })
      .onFailure(err -> {
        log.info("Inside on error {} ",err);
        this.mapExceptionToResponse(err);
      })
      .onComplete(asyncResultHandler);
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

  private CheckoutLock  mapToCheckOutLock(RowSet<Row> rowSet) {
    if(rowSet.size()==0)
        return null;
    var row = rowSet.iterator().next();
    CheckoutLock  checkoutLock = new CheckoutLock()
      .withId(row.getUUID("id").toString())
      .withUserId(row.getUUID("userid").toString())
      .withCreationDate(Date.valueOf(row.getLocalDate("creation_date")));
    log.info("mapToCheckOutLock:: checkoutLocks {}", checkoutLock);
    return checkoutLock;
  }

  private Response mapExceptionToResponse(Throwable t) {

    log.error(t.getMessage(), t);

    return Response.status(522)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(t.getMessage())
      .build();
  }
}
