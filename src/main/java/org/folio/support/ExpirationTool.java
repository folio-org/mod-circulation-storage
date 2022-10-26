package org.folio.support;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.folio.rest.impl.Headers.TENANT_HEADER;
import static org.folio.rest.jaxrs.model.Request.RequestLevel.ITEM;
import static org.folio.rest.jaxrs.model.Request.RequestLevel.TITLE;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_UNFILLED;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_DELIVERY;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_PICKUP;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;
import static org.folio.support.DbUtil.rowSetToStream;
import static org.folio.support.LogEventPayloadField.ORIGINAL;
import static org.folio.support.LogEventPayloadField.REQUESTS;
import static org.folio.support.LogEventPayloadField.UPDATED;
import static org.folio.support.ModuleConstants.REQUEST_TABLE;
import static org.folio.support.exception.LogEventType.REQUEST_EXPIRED;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.service.EventPublisherService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class ExpirationTool {
  private static final Logger log = LogManager.getLogger();
  private static final String JSONB_COLUMN = "jsonb";

  private ExpirationTool() {
    //do nothing
  }

  public static Future<Void> doRequestExpirationForTenant(Map<String, String> okapiHeaders, Vertx vertx) {
    Promise<Void> promise = Promise.promise();

    String tenant = okapiHeaders.get(TENANT_HEADER);

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);

    List<JsonObject> context = new ArrayList<>();

    pgClient.startTx(conn -> getExpiredRequests(conn, vertx, tenant)
      .compose(requests -> processExpiredRequests(conn, tenant, vertx, requests, context))
      .onComplete(v -> {
        if (v.failed()) {
          pgClient.rollbackTx(conn, done -> {
            log.error("Error in request processing", v.cause());
            promise.fail(v.cause());
          });
        } else {
          EventPublisherService eventPublisherService = new EventPublisherService(vertx, okapiHeaders);
          context.forEach(p -> eventPublisherService
            .publishLogRecord(new JsonObject().put(REQUESTS.value(), p), REQUEST_EXPIRED));
          pgClient.endTx(conn, done -> promise.complete());
        }
      }));

    return promise.future();
  }

  private static Future<Void> processExpiredRequests(AsyncResult<SQLConnection> conn,
    String tenant, Vertx vertx, List<Request> requests, List<JsonObject> context) {

    return processExpiredItemLevelRequests(conn, vertx, tenant, requests, context)
      .compose(v -> processExpiredTitleLevelRequests(conn, vertx, tenant, requests, context));
  }

  private static Future<Void> processExpiredItemLevelRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, List<Request> requests, List<JsonObject> context) {

    return closeItemLevelRequests(conn, vertx, tenant, requests, context)
      .compose(itemIds -> getOpenRequestsByItemIds(conn, vertx, tenant, itemIds))
      .compose(requestList -> reorderItemLevelRequests(conn, vertx, tenant, requestList));
  }

  private static Future<Void> processExpiredTitleLevelRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, List<Request> requests, List<JsonObject> context) {

    return closeTitleLevelRequests(conn, vertx, tenant, requests, context)
      .compose(instanceIds -> getOpenRequestsByInstanceIds(conn, vertx, tenant, instanceIds))
      .compose(requestList -> reorderTitleLevelRequests(conn, vertx, tenant, requestList));
  }

  private static Future<List<Request>> getExpiredRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant) {

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    df.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Promise<RowSet<Row>> promise = Promise.promise();

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
    pgClient.select(conn, query, promise);

    return promise.future()
      .map(rs -> rowSetToStream(rs)
        .map(row -> row.get(JsonObject.class, row.getColumnIndex(JSONB_COLUMN)))
        .map(json -> json.mapTo(Request.class))
        .collect(toList()));
  }

  private static Future<List<Request>> getOpenRequestsByItemIds(
    AsyncResult<SQLConnection> conn, Vertx vertx, String tenant, Set<String> itemIds) {

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

    return getOpenRequestsByAssociatedIds(conn, vertx, tenant, itemIds, where);
  }

  private static Future<List<Request>> getOpenRequestsByInstanceIds(
    AsyncResult<SQLConnection> conn, Vertx vertx, String tenant, Set<String> instanceIds) {

    if (instanceIds.isEmpty()) {
      return succeededFuture(emptyList());
    }

    Set<String> quotedItemIds = instanceIds.stream()
      .map(id -> format("'%s'", id))
      .collect(toSet());
    String where = format("WHERE " +
        "(jsonb->>'status' = '%1$s' OR " +
        "jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' OR " +
        "jsonb->>'status' = '%4$s') AND " +
        "jsonb->>'instanceId' IN (%5$s) " +
        "ORDER BY jsonb->>'position' ASC",

      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_AWAITING_DELIVERY.value(),
      OPEN_IN_TRANSIT.value(),
      String.join(",", quotedItemIds));

    return getOpenRequestsByAssociatedIds(conn, vertx, tenant, instanceIds, where);
  }

  private static Future<List<Request>> getOpenRequestsByAssociatedIds(
    AsyncResult<SQLConnection> conn, Vertx vertx, String tenant, Set<String> associatedIds,
    String query) {

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Promise<RowSet<Row>> promise = Promise.promise();

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(tenant),
      REQUEST_TABLE);
    String sql = format("SELECT jsonb FROM %s %s", fullTableName, query);
    pgClient.select(conn, sql, promise);

    return promise.future()
      .map(rs -> rowSetToStream(rs)
        .map(row -> row.get(JsonObject.class, row.getColumnIndex(JSONB_COLUMN)))
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

  private static Future<Void> reorderItemLevelRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, List<Request> requests) {

    if (requests.isEmpty()) {
      return succeededFuture();
    }

    Map<String, List<Request>> groupedRequests = requests.stream()
      .collect(Collectors.groupingBy(Request::getItemId));

    return resetPositionsForOpenRequestsByIdemIds(conn, vertx, tenant, groupedRequests.keySet())
      .compose(v -> reorderGroupedRequests(conn, vertx, tenant, groupedRequests));
  }

  private static Future<Void> reorderTitleLevelRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, List<Request> requests) {

    if (requests.isEmpty()) {
      return succeededFuture();
    }

    Map<String, List<Request>> groupedRequests = requests.stream()
      .collect(Collectors.groupingBy(Request::getInstanceId));

    return resetPositionsForOpenRequestsByInstanceIds(conn, vertx, tenant, groupedRequests.keySet())
      .compose(v -> reorderGroupedRequests(conn, vertx, tenant, groupedRequests));
  }

  private static Future<Void> reorderGroupedRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, Map<String, List<Request>> groupedRequests) {

    Future<Void> future = succeededFuture();
    for (Map.Entry<String, List<Request>> entry : groupedRequests.entrySet()) {
      future = future.compose(v -> updateRequestsPositions(conn, vertx, tenant, entry.getValue()));
    }

    return future;
  }

  private static Future<Void> updateRequestsPositions(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, List<Request> requests) {

    requests.sort(Comparator.comparingInt(Request::getPosition));
    AtomicInteger pos = new AtomicInteger(1);
    Future<Void> future = succeededFuture();

    for (Request request : requests) {
      future = future.compose(v -> updateRequest(conn, vertx, tenant, request.withPosition(pos.getAndIncrement())));
    }

    return future;
  }

  private static Future<Set<String>> closeItemLevelRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, List<Request> requests, List<JsonObject> context) {

    List<Request> itemLevelRequests = requests.stream()
      .filter(request -> request.getRequestLevel() == ITEM)
      .collect(toList());

    return closeRequests(conn, vertx, tenant, itemLevelRequests, context, Request::getItemId);
  }

  private static Future<Set<String>> closeTitleLevelRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, List<Request> requests, List<JsonObject> context) {

    List<Request> titleLevelRequests = requests.stream()
      .filter(request -> request.getRequestLevel() == TITLE)
      .collect(toList());

    return closeRequests(conn, vertx, tenant, titleLevelRequests, context, Request::getInstanceId);
  }

  private static Future<Set<String>> closeRequests(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, List<Request> requests, List<JsonObject> context,
    Function<Request, String> applyAssociatedId) {

    Future<Void> future = succeededFuture();
    Set<String> closedRequestsAssociatedIds = new HashSet<>();

    for (Request request : requests) {
      JsonObject pair = new JsonObject();
      pair.put(ORIGINAL.value(), JsonObject.mapFrom(request));
      closedRequestsAssociatedIds.add(applyAssociatedId.apply(request));
      Request updatedRequest = changeRequestStatus(request).withPosition(null);
      updatedRequest.getMetadata().withUpdatedDate(new Date());
      pair.put(UPDATED.value(), JsonObject.mapFrom(updatedRequest));
      context.add(pair);
      future = future.compose(v -> updateRequest(conn, vertx, tenant, updatedRequest));
    }

    return future.map(v -> closedRequestsAssociatedIds);
  }

  private static Future<RowSet<Row>> resetPositionsForOpenRequestsByIdemIds(
    AsyncResult<SQLConnection> conn, Vertx vertx, String tenant, Set<String> itemIds) {

    if (itemIds.isEmpty()) {
     return succeededFuture();
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

    return resetPositions(conn, vertx, tenant, sql);
  }

  private static Future<RowSet<Row>> resetPositionsForOpenRequestsByInstanceIds(
    AsyncResult<SQLConnection> conn, Vertx vertx, String tenant, Set<String> instanceIds) {

    if (instanceIds.isEmpty()) {
      return succeededFuture();
    }

    Set<String> quotedInstanceIds = instanceIds.stream()
      .map(id -> format("'%s'", id))
      .collect(toSet());

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(tenant),
      REQUEST_TABLE);

    String sql = format("UPDATE %1$s SET jsonb = jsonb - 'position' WHERE " +
        "(jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' OR " +
        "jsonb->>'status' = '%4$s' OR " +
        "jsonb->>'status' = '%5$s') AND " +
        "jsonb->>'instanceId' IN (%6$s)",
      fullTableName,
      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_AWAITING_DELIVERY.value(),
      OPEN_IN_TRANSIT.value(),
      String.join(",", quotedInstanceIds));

    return resetPositions(conn, vertx, tenant, sql);
  }

  private static Future<RowSet<Row>> resetPositions(AsyncResult<SQLConnection> conn,
    Vertx vertx, String tenant, String sql) {

    Promise<RowSet<Row>> promise = Promise.promise();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    pgClient.execute(conn, sql, promise);

    return promise.future().map(ur -> null);
  }

  private static Future<Void> updateRequest(AsyncResult<SQLConnection> conn,
     Vertx vertx, String tenant, Request request) {

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    Promise<RowSet<Row>> promise = Promise.promise();

    String where = format("WHERE jsonb->>'id' = '%s'", request.getId());
    pgClient.update(conn, REQUEST_TABLE, request, JSONB_COLUMN, where, false, promise);

    return promise.future().map(ur -> null);
  }

}
