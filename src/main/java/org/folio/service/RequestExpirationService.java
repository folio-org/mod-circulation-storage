package org.folio.service;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.folio.rest.impl.Headers.TENANT_HEADER;
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
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class RequestExpirationService {
  private static final Logger log = LogManager.getLogger();
  private static final String JSONB_COLUMN = "jsonb";
  private final String requestClassifierProperty;
  private final Function<Request, String> requestClassifier;
  private final PostgresClient pgClient;
  private final EventPublisherService eventPublisherService;

  public RequestExpirationService(Map<String, String> okapiHeaders, Vertx vertx,
    String requestClassifierProperty, Function<Request, String> requestClassifier) {

    this.requestClassifierProperty = requestClassifierProperty;
    this.requestClassifier = requestClassifier;
    pgClient = PostgresClient.getInstance(vertx, okapiHeaders.get(TENANT_HEADER));
    eventPublisherService = new EventPublisherService(vertx, okapiHeaders);
  }

  public Future<Void> doRequestExpiration() {
    List<JsonObject> context = new ArrayList<>();

    return pgClient.withTrans(conn -> getExpiredRequests(conn)
        .compose(expiredRequests -> closeRequests(conn, expiredRequests, context))
        .compose(associatedIds -> getOpenRequestsByIdFields(conn, associatedIds))
        .compose(openRequests -> reorderRequests(conn, openRequests))
        .map(x -> {
          context.forEach(p -> eventPublisherService
              .publishLogRecord(new JsonObject().put(REQUESTS.value(), p), REQUEST_EXPIRED));
          return (Void)null;
        }))
        .onFailure(e -> log.error("Error in request processing", e));
  }

  private Future<List<Request>> getExpiredRequests(Conn conn) {
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    df.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));

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

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(
      pgClient.getTenantId()), REQUEST_TABLE);
    String query = format("SELECT jsonb FROM %s %s", fullTableName, where);

    return conn.execute(query)
      .map(rs -> rowSetToStream(rs)
        .map(row -> row.get(JsonObject.class, row.getColumnIndex(JSONB_COLUMN)))
        .map(json -> json.mapTo(Request.class))
        .collect(toList()));
  }

  private Future<List<Request>> getOpenRequestsByIdFields(Conn conn,
    Set<String> idFields) {

    if (idFields.isEmpty()) {
      return succeededFuture(emptyList());
    }

    String quotedFieldIds = idFields.stream()
      .map(id -> format("'%s'", id))
      .collect(Collectors.joining(","));
    String where = format("WHERE " +
        "(jsonb->>'status' = '%1$s' OR " +
        "jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' OR " +
        "jsonb->>'status' = '%4$s') AND " +
        "jsonb->>'%5$s' IN (%6$s) " +
        "ORDER BY jsonb->>'position' ASC",

      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_AWAITING_DELIVERY.value(),
      OPEN_IN_TRANSIT.value(),
      requestClassifierProperty,
      quotedFieldIds);

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(
      pgClient.getTenantId()), REQUEST_TABLE);
    String sql = format("SELECT jsonb FROM %s %s", fullTableName, where);

    return conn.execute(sql)
      .map(rs -> rowSetToStream(rs)
        .map(row -> row.get(JsonObject.class, row.getColumnIndex(JSONB_COLUMN)))
        .map(json -> json.mapTo(Request.class))
        .collect(toList()));
  }

  private Request changeRequestStatus(Request request) {
    if (request.getStatus() == OPEN_NOT_YET_FILLED ||
          request.getStatus() == OPEN_IN_TRANSIT ||
          request.getStatus() == OPEN_AWAITING_DELIVERY) {
      request.setStatus(CLOSED_UNFILLED);
    } else if (request.getStatus() == OPEN_AWAITING_PICKUP) {
      request.setStatus(CLOSED_PICKUP_EXPIRED);
    }
    return request;
  }

  private Future<Void> reorderRequests(Conn conn,
    List<Request> requests) {

    if (requests.isEmpty()) {
      return succeededFuture();
    }

    Map<String, List<Request>> groupedRequests = requests.stream()
      .collect(Collectors.groupingBy(requestClassifier));

    return resetPositionsForOpenRequests(conn, groupedRequests.keySet())
      .compose(v -> reorderGroupedRequests(conn, groupedRequests));
  }

  private Future<Void> reorderGroupedRequests(Conn conn,
    Map<String, List<Request>> groupedRequests) {

    Future<Void> future = succeededFuture();
    for (Map.Entry<String, List<Request>> entry : groupedRequests.entrySet()) {
      future = future.compose(v -> updateRequestsPositions(conn, entry.getValue()));
    }

    return future;
  }

  private Future<Void> updateRequestsPositions(Conn conn,
    List<Request> requests) {

    requests.sort(Comparator.comparingInt(Request::getPosition));
    AtomicInteger pos = new AtomicInteger(1);
    Future<Void> future = succeededFuture();

    for (Request request : requests) {
      future = future.compose(v -> updateRequest(conn, request.withPosition(
        pos.getAndIncrement())));
    }

    return future;
  }

  private Future<Set<String>> closeRequests(Conn conn,
    List<Request> requests, List<JsonObject> context) {

    Future<Void> future = succeededFuture();
    Set<String> closedRequestsAssociatedIds = new HashSet<>();

    for (Request request : requests) {
      JsonObject pair = new JsonObject();
      pair.put(ORIGINAL.value(), JsonObject.mapFrom(request));
      closedRequestsAssociatedIds.add(requestClassifier.apply(request));
      Request updatedRequest = changeRequestStatus(request).withPosition(null);
      updatedRequest.getMetadata().withUpdatedDate(new Date());
      pair.put(UPDATED.value(), JsonObject.mapFrom(updatedRequest));
      context.add(pair);
      future = future.compose(v -> updateRequest(conn, updatedRequest));
    }

    return future.map(v -> closedRequestsAssociatedIds);
  }

  private Future<RowSet<Row>> resetPositionsForOpenRequests(Conn conn,
    Set<String> associatedIds) {

    if (associatedIds.isEmpty()) {
      return succeededFuture();
    }

    String quotedFieldIds = associatedIds.stream()
      .map(id -> format("'%s'", id))
      .collect(Collectors.joining(","));

    String fullTableName = format("%s.%s", PostgresClient.convertToPsqlStandard(
      pgClient.getTenantId()), REQUEST_TABLE);

    String sql = format("UPDATE %1$s SET jsonb = jsonb - 'position' WHERE " +
        "(jsonb->>'status' = '%2$s' OR " +
        "jsonb->>'status' = '%3$s' OR " +
        "jsonb->>'status' = '%4$s' OR " +
        "jsonb->>'status' = '%5$s') AND " +
        "jsonb->>'%6$s' IN (%7$s)",
      fullTableName,
      OPEN_NOT_YET_FILLED.value(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_AWAITING_DELIVERY.value(),
      OPEN_IN_TRANSIT.value(),
      requestClassifierProperty,
      quotedFieldIds);

    return conn.execute(sql).mapEmpty();
  }

  private Future<Void> updateRequest(Conn conn, Request request) {
    return conn.update(REQUEST_TABLE, request, request.getId()).mapEmpty();
  }
}
