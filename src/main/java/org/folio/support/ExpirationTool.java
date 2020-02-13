package org.folio.support;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.folio.rest.impl.RequestsAPI.REQUEST_TABLE;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_UNFILLED;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_DELIVERY;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_PICKUP;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

public class ExpirationTool {

  private static final Logger log = LoggerFactory.getLogger(ExpirationTool.class);

  private ExpirationTool() {
    //do nothing
  }

  public static Future<Void> doRequestExpiration(Vertx vertx) {

    Promise<ResultSet> promise = Promise.promise();
    PostgresClient pgClient = PostgresClient.getInstance(vertx);
    String tenantQuery = "select nspname from pg_catalog.pg_namespace where nspname LIKE '%_mod_circulation_storage';";
    pgClient.select(tenantQuery, promise.future());

    return promise.future().compose(rs -> CompositeFuture.all(rs.getRows()
      .stream()
      .map(row -> doRequestExpirationForTenant(vertx, getTenant(row.getString("nspname"))))
      .collect(toList()))
      .map(all -> null));
  }

  private static Future<Void> doRequestExpirationForTenant(Vertx vertx, String tenant) {

    Promise<Void> promise = Promise.promise();

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);

    pgClient.startTx(conn -> getExpiredRequests(conn, vertx, tenant)
      .compose(requests -> closeRequests(conn, vertx, tenant, requests))
      .compose(itemIds -> getOpenRequestsByItemIds(conn, vertx, tenant, itemIds))
      .compose(requests -> reorderRequests(conn, vertx, tenant, requests))
      .setHandler(v -> {
        if (v.failed()) {
          pgClient.rollbackTx(conn, done -> {
            log.error("Error in request processing", v.cause());
            promise.fail(v.cause());
          });
        } else {
          pgClient.endTx(conn, done -> promise.complete());
        }
      }));

    return promise.future();
  }

  private static Future<List<Request>> getExpiredRequests(AsyncResult<SQLConnection> conn, Vertx vertx, String tenant) {

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    df.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Promise<ResultSet> promise = Promise.promise();

    String where = format("WHERE " +
        "(jsonb->>'status' = '%1$s' AND jsonb->>'requestExpirationDate' < '%5$s') OR " +
        "(jsonb->>'status' = '%2$s' AND jsonb->>'requestExpirationDate' < '%5$s') OR " +
        "(jsonb->>'status' = '%3$s' AND jsonb->>'requestExpirationDate' < '%5$s') OR " +
        "(jsonb->>'status' = '%4$s' AND jsonb->>'holdShelfExpirationDate' < '%5$s') " +
        "LIMIT 150",

      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_DELIVERY.value(),
      OPEN_IN_TRANSIT.value(),
      OPEN_AWAITING_PICKUP.value(),
      df.format(new Date()));


    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(tenant), REQUEST_TABLE);
    String query = format("SELECT jsonb FROM %s %s", fullTableName, where);
    pgClient.select(conn, query, promise.future());

    return promise.future().map(ResultSet::getRows)
      .map(rows -> rows.stream()
        .map(row -> row.getString("jsonb"))
        .map(JsonObject::new)
        .map(json -> json.mapTo(Request.class))
        .collect(toList()));

  }

  private static Future<List<Request>> getOpenRequestsByItemIds(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                                String tenant, Set<String> itemIds) {

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Promise<ResultSet> promise = Promise.promise();

    if (itemIds.isEmpty()) {
      return succeededFuture(emptyList());
    }

    Set<String> quotedItemIds = itemIds.stream()
      .map(id -> format("'%s'", id))
      .collect(toSet());

    String where = format("WHERE " +
        "(jsonb->>'status' = '%1$s' OR " +
        "jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' OR " +
        "jsonb->>'status' = '%4$s') AND " +
        "jsonb->>'itemId' IN (%5$s) " +
        "ORDER BY jsonb->>'position' ASC",

      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_AWAITING_DELIVERY.value(),
      OPEN_IN_TRANSIT.value(),
      String.join(",", quotedItemIds));

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(tenant), REQUEST_TABLE);
    String sql = format("SELECT jsonb FROM %s %s", fullTableName, where);
    pgClient.select(conn, sql, promise.future());

    return promise.future().map(ResultSet::getRows)
      .map(rows -> rows.stream()
        .map(row -> row.getString("jsonb"))
        .map(JsonObject::new)
        .map(json -> json.mapTo(Request.class))
        .collect(toList()));
  }

  private static Request changeRequestStatus(Request request) {
    if (request.getStatus() == OPEN_NOT_YET_FILLED ||
          request.getStatus() == OPEN_IN_TRANSIT ||
          request.getStatus() == OPEN_AWAITING_DELIVERY) {
      request.setStatus(CLOSED_UNFILLED);
    } else if (request.getStatus() == OPEN_AWAITING_PICKUP) {
      request.setStatus(CLOSED_PICKUP_EXPIRED);
    }
    return request;
  }

  private static Future<Void> reorderRequests(AsyncResult<SQLConnection> conn, Vertx vertx,
                                              String tenant, List<Request> requests) {

    if (requests.isEmpty()) {
      return succeededFuture();
    }

    Map<String, List<Request>> groupedRequests = requests.stream()
      .collect(Collectors.groupingBy(Request::getItemId));

    return resetPositionsForOpenRequestsByIdemIds(conn, vertx, tenant, groupedRequests.keySet())
      .compose(v -> reorderRequestsForEachItem(conn, vertx, tenant, groupedRequests));
  }

  private static Future<Void> reorderRequestsForEachItem(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                         String tenant, Map<String, List<Request>> groupedRequests) {

    Future<Void> future = succeededFuture();
    for (Map.Entry<String, List<Request>> entry : groupedRequests.entrySet()) {
      future = future.compose(v -> updateRequestsPositions(conn, vertx, tenant, entry.getValue()));
    }

    return future;
  }

  private static Future<Void> updateRequestsPositions(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                      String tenant, List<Request> requests) {

    requests.sort(Comparator.comparingInt(Request::getPosition));
    AtomicInteger pos = new AtomicInteger(1);
    Future<Void> future = succeededFuture();

    for (Request request : requests) {
      future = future.compose(v -> updateRequest(conn, vertx, tenant, request.withPosition(pos.getAndIncrement())));
    }

    return future;
  }


  private static Future<Set<String>> closeRequests(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                   String tenant, List<Request> requests) {


    Future<Void> future = succeededFuture();
    Set<String> closedRequestsItemIds = new HashSet<>();

    for (Request request : requests) {
      closedRequestsItemIds.add(request.getItemId());
      Request updatedRequest = changeRequestStatus(request).withPosition(null);
      future = future.compose(v -> updateRequest(conn, vertx, tenant, updatedRequest));
    }

    return future.map(v -> closedRequestsItemIds);
  }

  private static Future<UpdateResult> resetPositionsForOpenRequestsByIdemIds(AsyncResult<SQLConnection> conn, Vertx vertx,
                                                                             String tenant, Set<String> itemIds) {

    if (itemIds.isEmpty()) {
     succeededFuture();
    }

    Set<String> quotedItemIds = itemIds.stream()
      .map(id -> format("'%s'", id))
      .collect(toSet());

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(tenant), REQUEST_TABLE);

    String sql = format("UPDATE %1$s SET jsonb = jsonb - 'position' WHERE " +
        "(jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' OR " +
        "jsonb->>'status' = '%4$s' OR " +
        "jsonb->>'status' = '%5$s') AND " +
        "jsonb->>'itemId' IN (%6$s)",

      fullTableName,
      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_AWAITING_DELIVERY.value(),
      OPEN_IN_TRANSIT.value(),
      String.join(",", quotedItemIds));

    Promise<UpdateResult> promise = Promise.promise();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    pgClient.execute(conn, sql, promise.future());

    return promise.future().map(ur -> null);
  }

  private static Future<Void> updateRequest(AsyncResult<SQLConnection> conn, Vertx vertx,
                                            String tenant, Request request) {

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Promise<UpdateResult> promise = Promise.promise();

    String where = format("WHERE jsonb->>'id' = '%s'", request.getId());
    pgClient.update(conn, REQUEST_TABLE, request, "jsonb", where, false, promise.future());

    return promise.future().map(ur -> null);
  }

  private static String getTenant(String nsTenant) {

    String suffix = "_mod_circulation_storage";
    int suffixLength = nsTenant.length() - suffix.length();
    return nsTenant.substring(0, suffixLength);
  }
}
