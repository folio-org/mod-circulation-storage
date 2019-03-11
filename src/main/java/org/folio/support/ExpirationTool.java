package org.folio.support;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.ResultSet;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.folio.rest.impl.RequestsAPI.REQUEST_TABLE;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_PICKUP_EXPIRED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_UNFILLED;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_PICKUP;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;

public class ExpirationTool {

  private static final String QUERY_TO_LOOK_FOR_EXPIRED_UNFILLED = "(status == \"%s\" AND requestExpirationDate < \"%s\")";
  private static final String QUERY_TO_LOOK_FOR_EXPIRED_AWAITING_PICKUP = "(status == \"%s\" AND holdShelfExpirationDate < \"%s\")";

  private ExpirationTool() {
    //do nothing
  }

  public static Future<Void> doRequestExpiration(Vertx vertx, Context context) {
    Future<ResultSet> future = Future.future();

    PostgresClient pgClient = PostgresClient.getInstance(vertx);
    String tenantQuery = "select nspname from pg_catalog.pg_namespace where nspname LIKE '%_mod_circulation_storage';";
    pgClient.select(tenantQuery, future.completer());

    return future.compose(rs -> CompositeFuture.all(rs.getRows()
      .stream()
      .map(row -> doRequestExpirationForTenant(vertx, getTenant(row.getString("nspname"))))
      .collect(Collectors.toList())))
      .map(compositeFuture -> null);
  }

  public static Future<Integer> doRequestExpirationForTenant(Vertx vertx, String tenant) {
    Future<Integer> future = Future.future();
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String nowDateString = simpleDateFormat.format(new Date());
    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    pgClient.setIdField("_id");
    String query = String.format(
      String.format("%s OR %s", QUERY_TO_LOOK_FOR_EXPIRED_UNFILLED, QUERY_TO_LOOK_FOR_EXPIRED_AWAITING_PICKUP),
      OPEN_NOT_YET_FILLED.value(), nowDateString, OPEN_AWAITING_PICKUP.value(), nowDateString);
    try {
      pgClient.get(REQUEST_TABLE, Request.class, new String[]{"*"}, initCqlWrapper(query), true, false, reply -> {
        if (reply.failed()) {
          future.fail(reply.cause());
        } else if (reply.result().getResults().isEmpty()) {
          future.tryComplete();
        } else {
          List<Request> requestList = reply.result().getResults();
          List<Future> futureList = closeRequests(vertx, tenant, requestList);
          CompositeFuture.join(futureList)
            .setHandler(compRes -> future.complete(countSucceeded(futureList)));
        }
      });
    } catch (FieldException e) {
      future.fail(e.getLocalizedMessage());
    }
    return future;
  }

  /* For each expired request set Closed status and remove position*/
  private static List<Future> closeRequests(Vertx vertx, String tenant, List<Request> requestList) {

    return requestList.stream()
      .map(ExpirationTool::changeRequestStatus)
      .map(req -> req.withPosition(null))
      .map(req -> saveRequest(vertx, tenant, req)
        .compose(v -> updateRequestQueue(vertx, tenant, req)))
      .collect(Collectors.toList());
  }

  private static Request changeRequestStatus(Request request) {
    if (request.getStatus() == Request.Status.OPEN_NOT_YET_FILLED) {
      request.setStatus(CLOSED_UNFILLED);
    } else if (request.getStatus() == Request.Status.OPEN_AWAITING_PICKUP) {
      request.setStatus(CLOSED_PICKUP_EXPIRED);
    }
    return request;
  }

  /* update request queue, request positions adjustment with same itemId */
  private static Future<Void> updateRequestQueue(Vertx vertx, String tenant, Request request) {
    Future<Void> future = Future.future();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    pgClient.setIdField("_id");
    String requestQueueQuery = String.format(
      "itemId==%s and status==(\"%s\" or \"%s\" or \"%s\") sortBy position/sort.ascending",
      request.getItemId(),
      OPEN_AWAITING_PICKUP.value(),
      OPEN_NOT_YET_FILLED.value(),
      OPEN_IN_TRANSIT.value());
    try {
      pgClient.get(REQUEST_TABLE, Request.class, new String[]{"*"}, initCqlWrapper(requestQueueQuery), true, false, res -> {
        if (!res.failed()) {
          List<Request> list = res.result().getResults();
          if (list.isEmpty()) {
            future.tryComplete();
          } else {
            Future<Void> cleanFuture = cleanRequestPositions(vertx, tenant, request.getItemId());
            cleanFuture.setHandler(cleanReply -> {
              if (cleanReply.succeeded()) {
                List<Future> reorderList = reorderRequests(vertx, tenant, res.result().getResults());
                CompositeFuture.join(reorderList).setHandler(compRes -> future.complete());
              } else {
                future.fail(cleanReply.cause());
              }
            });
          }
        } else {
          future.fail(res.cause());
        }
      });
    } catch (FieldException e) {
      future.fail(e.getLocalizedMessage());
    }
    return future;
  }

  /* positions should be removed for each request with itemId before adjustment due to itemId-position unique index */
  private static Future<Void> cleanRequestPositions(Vertx vertx, String tenant, String itemId) {
    Future<Void> future = Future.future();
    //Get a list of tenants
    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
    String cleanQuery = String.format("UPDATE %1$s.%2$s SET jsonb = jsonb - 'position' WHERE jsonb->>'itemId' = '%3$s'",
      PostgresClient.convertToPsqlStandard(tenant), REQUEST_TABLE, itemId);
    pgClient.execute(cleanQuery, reply -> {
      if (reply.failed()) {
        future.fail(reply.cause());
      } else {
        future.complete();
      }
    });
    return future;
  }

  private static List<Future> reorderRequests(Vertx vertx, String tenant, List<Request> requestList) {
    List<Future> futureList = new ArrayList<>();
    int currentPosition = 1;
    for (Request request : requestList) {
      final int position = currentPosition;
      Future<Void> saveFuture = saveRequest(vertx, tenant, request.withPosition(position));
      futureList.add(saveFuture);
      currentPosition++;
    }
    return futureList;
  }

  private static Future<Void> saveRequest(Vertx vertx, String tenant, Request request) {
    Future<Void> future = Future.future();
    try {
      PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
      pgClient.setIdField("_id");
      Criteria idCrit = new Criteria("ramls/request.json");
      idCrit.addField("'id'").setOperation("=").setValue(request.getId());
      Criterion criterion = new Criterion(idCrit);

      pgClient.update(REQUEST_TABLE, request, criterion, true, updateReply -> {
        if (updateReply.failed()) {
          future.fail(updateReply.cause());
        } else {
          future.complete();
        }
      });
    } catch (Exception e) {
      future.tryFail(e);
    }
    return future;
  }

  private static CQLWrapper initCqlWrapper(String query) throws FieldException {
    CQL2PgJSON cql2pgJson;
    cql2pgJson = new CQL2PgJSON(Collections.singletonList(REQUEST_TABLE + ".jsonb"));
    return new CQLWrapper(cql2pgJson, query);
  }

  private static int countSucceeded(List<Future> futureList) {
    int succeededCount = 0;
    for (Future fut : futureList) {
      if (fut.succeeded()) {
        succeededCount++;
      }
    }
    return succeededCount;
  }

  private static String getTenant(String nsTenant) {
    String suffix = "_mod_circulation_storage";
    int suffixLength = nsTenant.length() - suffix.length();
    return nsTenant.substring(0, suffixLength);
  }
}
