package org.folio.support;

import static java.lang.String.format;

import static org.folio.rest.impl.RequestsAPI.REQUEST_TABLE;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_UNFILLED;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_PICKUP;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.PostgresClient;

public class ExpirationTool {

  private ExpirationTool() {
    //do nothing
  }

  public static Future<Void> doRequestExpiration(Vertx vertx) {

    Future<ResultSet> future = Future.future();
    PostgresClient pgClient = PostgresClient.getInstance(vertx);
    String tenantQuery = "select nspname from pg_catalog.pg_namespace where nspname LIKE '%_mod_circulation_storage';";
    pgClient.select(tenantQuery, future.completer());

    return future.compose(rs -> CompositeFuture.all(rs.getRows()
      .stream()
      .map(row -> doRequestExpirationForTenant(vertx, getTenant(row.getString("nspname"))))
      .collect(Collectors.toList()))
      .map(all -> null));
  }

  private static Future<Void> doRequestExpirationForTenant(Vertx vertx, String tenant) {

    Future<Void> future = Future.future();

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);

    pgClient.startTx(conn -> getExpiredRequests(conn, vertx, tenant)
      .compose(requests -> closeRequests(conn, vertx, tenant, requests))
      .compose(v -> getOpenRequests(conn, vertx, tenant))
      .compose(requests -> reorderRequests(conn, vertx, tenant, requests))
      .setHandler(v -> {
        if (v.failed()) {
          pgClient.rollbackTx(conn, done -> future.fail(v.cause()));
        } else {
          pgClient.endTx(conn, done -> future.complete());
        }
      }));

    return future;
  }

  private static Future<List<Request>> getExpiredRequests(AsyncResult<SQLConnection> conn, Vertx vertx, String tenant) {

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    df.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Future<ResultSet> future = Future.future();

    String where = format("WHERE " +
        "(jsonb->>'status' = '%1$s' AND jsonb->>'requestExpirationDate' < '%3$s') OR " +
        "(jsonb->>'status' = '%2$s' AND jsonb->>'holdShelfExpirationDate' < '%3$s')",

      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      df.format(new Date()));


    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(tenant), REQUEST_TABLE);
    String query = format("SELECT jsonb FROM %s %s", fullTableName, where);
    pgClient.select(conn, query, future);

    return future.map(ResultSet::getRows)
      .map(rows -> rows.stream()
        .map(row -> row.getString("jsonb"))
        .map(JsonObject::new)
        .map(json -> json.mapTo(Request.class))
        .collect(Collectors.toList()));

  }

  private static Future<List<Request>> getOpenRequests(AsyncResult<SQLConnection> conn, Vertx vertx, String tenant) {

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Future<ResultSet> future = Future.future();

    String where = format("WHERE " +
        "jsonb->>'status' = '%1$s' OR " +
        "jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' " +
        "ORDER BY jsonb->>'position' ASC",

      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_IN_TRANSIT.value());

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(tenant), REQUEST_TABLE);
    String sql = format("SELECT jsonb FROM %s %s", fullTableName, where);
    pgClient.select(conn, sql, future.completer());

    return future.map(ResultSet::getRows)
      .map(rows -> rows.stream()
        .map(row -> row.getString("jsonb"))
        .map(JsonObject::new)
        .map(json -> json.mapTo(Request.class))
        .collect(Collectors.toList()));
  }

  private static Request changeRequestStatus(Request request) {
    if (request.getStatus() == OPEN_NOT_YET_FILLED) {
      request.setStatus(CLOSED_UNFILLED);
    } else if (request.getStatus() == OPEN_AWAITING_PICKUP) {
      request.setStatus(CLOSED_PICKUP_EXPIRED);
    }
    return request;
  }

  private static Future<Void> reorderRequests(AsyncResult<SQLConnection> conn, Vertx vertx,
                                              String tenant,List<Request> requests) {

    return resetPositionsForOpenRequests(conn, vertx, tenant)
      .compose(v -> reorderRequestsForEachItem(conn, vertx, tenant, requests));
  }

  private static Future<Void> reorderRequestsForEachItem(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                         String tenant, List<Request> requests) {

    Map<String, List<Request>> map = requests.stream()
      .collect(Collectors.groupingBy(Request::getItemId));

    Future<Void> future = Future.succeededFuture();
    for (Map.Entry<String, List<Request>> entry : map.entrySet()) {
      future = future.compose(v -> updateRequestsPositions(conn, vertx, tenant, entry.getValue()));
    }

    return future;
  }

  private static Future<Void> updateRequestsPositions(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                      String tenant, List<Request> requests) {

    AtomicInteger pos = new AtomicInteger(1);
    Future<Void> future = Future.succeededFuture();

    for (Request request : requests) {
      future = future.compose(v -> updateRequest(conn, vertx, tenant, request.withPosition(pos.getAndIncrement())));
    }

    return future;
  }


  private static Future<Void> closeRequests(AsyncResult<SQLConnection> conn, Vertx vertx,
                                            String tenant, List<Request> requests) {


    Future<Void> future = Future.succeededFuture();

    for (Request request : requests) {
      Request updatedRequest = changeRequestStatus(request).withPosition(null);
      future = future.compose(v -> updateRequest(conn, vertx, tenant, updatedRequest));
    }

    return future;
  }

  private static Future<UpdateResult> resetPositionsForOpenRequests(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                                    String tenant) {

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(tenant), REQUEST_TABLE);

    String sql = format("UPDATE %1$s SET jsonb = jsonb - 'position' WHERE " +
        "jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' OR " +
        "jsonb->>'status' = '%4$s'",

      fullTableName,
      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_IN_TRANSIT.value());

    Future<UpdateResult> future = Future.future();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    pgClient.execute(conn, sql, future.completer());

    return future;
  }

  private static Future<Void> updateRequest(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                    String tenant, Request request) {

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Future<UpdateResult> future = Future.future();

    String where = format("WHERE jsonb->>'id' = '%s'", request.getId());
    pgClient.update(conn, REQUEST_TABLE, request, "jsonb", where, false, future.completer());

    return future.map(ur -> null);
  }

  private static String getTenant(String nsTenant) {

    String suffix = "_mod_circulation_storage";
    int suffixLength = nsTenant.length() - suffix.length();
    return nsTenant.substring(0, suffixLength);
  }

}
