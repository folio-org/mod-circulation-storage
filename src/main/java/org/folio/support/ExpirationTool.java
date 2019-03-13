package org.folio.support;

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

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.interfaces.Results;

public class ExpirationTool {

  private ExpirationTool() {
    //do nothing
  }

  public static Future<CompositeFuture> doRequestExpiration(Vertx vertx) {

    Future<ResultSet> future = Future.future();
    PostgresClient pgClient = PostgresClient.getInstance(vertx);
    String tenantQuery = "select nspname from pg_catalog.pg_namespace where nspname LIKE '%_mod_circulation_storage';";
    pgClient.select(tenantQuery, future.completer());

    return future.compose(rs -> CompositeFuture.all(rs.getRows()
      .stream()
      .map(row -> doRequestExpirationForTenant(vertx, getTenant(row.getString("nspname"))))
      .collect(Collectors.toList())));
  }

  private static Future<CompositeFuture> doRequestExpirationForTenant(Vertx vertx, String tenant) {

    return getExpiredRequests(vertx, tenant)
      .compose(requests -> closeRequests(vertx, tenant, requests))
      .compose(v -> getOpenRequests(vertx, tenant))
      .compose(requests -> reorderRequests(vertx, tenant, requests));
  }

  private static Future<List<Request>> getExpiredRequests(Vertx vertx, String tenant) {

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    df.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Future<Results<Request>> future = Future.future();

    String where = String.format("WHERE " +
        "(jsonb->>'status' = '%1$s' AND jsonb->>'requestExpirationDate' < '%3$s') OR " +
        "(jsonb->>'status' = '%2$s' AND jsonb->>'holdShelfExpirationDate' < '%3$s')",

      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      df.format(new Date()));

    pgClient.get(REQUEST_TABLE, Request.class, "jsonb", where, false, false, false, future.completer());

    return future.map(Results::getResults);
  }

  private static Future<List<Request>> getOpenRequests(Vertx vertx, String tenant) {

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Future<Results<Request>> future = Future.future();

    String where = String.format("WHERE " +
        "jsonb->>'status' = '%1$s' OR " +
        "jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' " +
        "ORDER BY jsonb->>'position' ASC",

      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_IN_TRANSIT.value());

    pgClient.get(REQUEST_TABLE, Request.class, "jsonb", where, false, false, false, future.completer());

    return future.map(Results::getResults);
  }

  private static Request changeRequestStatus(Request request) {
    if (request.getStatus() == OPEN_NOT_YET_FILLED) {
      request.setStatus(CLOSED_UNFILLED);
    } else if (request.getStatus() == OPEN_AWAITING_PICKUP) {
      request.setStatus(CLOSED_PICKUP_EXPIRED);
    }
    return request;
  }

  private static Request eraseRequestPosition(Request request) {
    return request.withPosition(null);
  }

  private static Future<CompositeFuture> reorderRequests(Vertx vertx, String tenant, List<Request> requests) {

    return resetPositionsForOpenRequests(vertx, tenant)
      .compose(v -> reorderRequestsForEachItem(vertx, tenant, requests));
  }

  private static Future<CompositeFuture> reorderRequestsForEachItem(Vertx vertx, String tenant, List<Request> requests) {

    Map<String, List<Request>> map = requests.stream()
      .collect(Collectors.groupingBy(Request::getItemId));

    return CompositeFuture.all(map.entrySet()
      .stream()
      .map(entry -> updateRequestsPositions(vertx, tenant, entry.getValue()))
      .collect(Collectors.toList()));
  }

  private static Future<CompositeFuture> updateRequestsPositions(Vertx vertx, String tenant, List<Request> requests) {

    AtomicInteger pos = new AtomicInteger(1);

    return CompositeFuture.all(requests.stream()
      .map(request -> updateRequest(vertx, tenant, request.withPosition(pos.getAndIncrement())))
      .collect(Collectors.toList()));
  }


  private static Future<CompositeFuture> closeRequests(Vertx vertx, String tenant, List<Request> requests) {

    return CompositeFuture.all(requests.stream()
      .map(ExpirationTool::changeRequestStatus)
      .map(ExpirationTool::eraseRequestPosition)
      .map(request -> updateRequest(vertx, tenant, request))
      .collect(Collectors.toList()));
  }

  private static Future<UpdateResult> resetPositionsForOpenRequests(Vertx vertx, String tenant) {

    String fullTableName = String.format("%s.%s", PostgresClient.convertToPsqlStandard(tenant), REQUEST_TABLE);

    String query = String.format("UPDATE %1$s SET jsonb = jsonb - 'position' " +
        "WHERE " +
        "jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' OR " +
        "jsonb->>'status' = '%4$s'",

      fullTableName,
      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_IN_TRANSIT.value());

    Future<UpdateResult> future = Future.future();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    pgClient.execute(query,future.completer());

    return future;
  }

  private static Future<UpdateResult> updateRequest(Vertx vertx, String tenant, Request request) {

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Future<UpdateResult> future = Future.future();
    pgClient.update(REQUEST_TABLE, request, request.getId(), future.completer());

    return future;
  }

  private static String getTenant(String nsTenant) {

    String suffix = "_mod_circulation_storage";
    int suffixLength = nsTenant.length() - suffix.length();
    return nsTenant.substring(0, suffixLength);
  }

}
