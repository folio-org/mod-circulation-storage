package org.folio.support;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import static org.folio.rest.impl.RequestsAPI.REQUEST_TABLE;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_UNFILLED;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_PICKUP;
import static org.folio.rest.jaxrs.model.Request.Status.CLOSED_PICKUP_EXPIRED;

public class ExpirationTool {

  private ExpirationTool() {
    //do nothing
  }

  public static void doRequestExpiration(Vertx vertx, Context context) {
    final Logger logger = LoggerFactory.getLogger(ExpirationTool.class);
    logger.info("Calling doRequestExpiration()");
    context.runOnContext(v -> {
      //Get a list of tenants
      PostgresClient pgClient = PostgresClient.getInstance(vertx);
      String tenantQuery = "select nspname from pg_catalog.pg_namespace where nspname LIKE '%_mod_circulation_storage';";
      pgClient.select(tenantQuery, reply -> {
        if(reply.succeeded()) {
          List<JsonObject> obList = reply.result().getRows();
          for(JsonObject ob : obList) {
            String nsTenant = ob.getString("nspname");
            String suffix = "_mod_circulation_storage";
            int suffixLength = nsTenant.length() - suffix.length();
            final String tenant = nsTenant.substring(0, suffixLength);
            logger.info("Calling doRequestExpirationForTenant for tenant " + tenant);
            Future<Integer> expireFuture = doRequestExpirationForTenant(vertx, context, tenant);
            expireFuture.setHandler(res -> {
              if(res.failed()) {
                logger.info(String.format("Attempt to expire records for tenant %s failed: %s",
                  tenant, res.cause().getLocalizedMessage()));
              } else {
                logger.info(String.format("Expired %s requests", res.result()));
              }
            });
          }
        } else {
          logger.info(String.format("TenantQuery '%s' failed: %s", tenantQuery,
            reply.cause().getLocalizedMessage()));
        }
      });
    });
  }

  public static Future<Integer> doRequestExpirationForTenant(Vertx vertx, Context context, String tenant) {
    final Logger logger = LoggerFactory.getLogger(ExpirationTool.class);
    Future<Integer> future = Future.future();
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String nowDateString =  simpleDateFormat.format(new Date());
    context.runOnContext(v -> {
      PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
      pgClient.setIdField("id");
      String query = String.format("(status == \"%s\" AND requestExpirationDate < %s) OR (status == \"%s\" AND holdShelfExpirationDate < %s)",
        OPEN_NOT_YET_FILLED.value(), nowDateString, OPEN_AWAITING_PICKUP.value(), nowDateString);
      CQL2PgJSON cql2pgJson;
      CQLWrapper cqlWrapper;
      String[] fieldList = {"*"};
      try {
        cql2pgJson = new CQL2PgJSON(Collections.singletonList(REQUEST_TABLE + ".jsonb"));
        cqlWrapper = new CQLWrapper(cql2pgJson, query);
      } catch(Exception e) {
        future.fail(e.getLocalizedMessage());
        return;
      }
      pgClient.get(REQUEST_TABLE, Request.class, fieldList, cqlWrapper, true, false, reply -> {
        if(reply.failed()) {
          logger.info(String.format("Error executing postgres query: '%s', %s",
            query, reply.cause().getLocalizedMessage()));
          future.fail(reply.cause());
        } else if(reply.result().getResults().isEmpty()) {
          logger.info(String.format("No results found for query %s", query));
        } else {
          List<Request> requestList = reply.result().getResults();
          List<Future> futureList = new ArrayList<>();
          for(Request request : requestList) {
            if (request.getStatus() == OPEN_NOT_YET_FILLED) {
              request.setStatus(CLOSED_UNFILLED);
            } else {
              request.setStatus(CLOSED_PICKUP_EXPIRED);
            }
            Future<Void> saveFuture = saveRequest(vertx, context, tenant, request);
            futureList.add(saveFuture);
          }
          CompositeFuture compositeFuture = CompositeFuture.join(futureList);
          compositeFuture.setHandler(compRes -> {
            int succeededCount = 0;
            for(Future fut : futureList) {
              if(fut.succeeded()) {
                succeededCount++;
              }
            }
            future.complete(succeededCount);
          });
        }
      });
    });
    return future;
  }

  public static Future<Void> saveRequest(Vertx vertx, Context context, String tenant, Request request) {
    final Logger logger = LoggerFactory.getLogger(ExpirationTool.class);
    logger.info(String.format("Updating request with id %s", request.getId()));
    Future<Void> future = Future.future();
    context.runOnContext(v -> {
      try {
        PostgresClient pgClient = PostgresClient.getInstance(vertx, tenant);
        pgClient.setIdField("_id");
        Criteria idCrit = new Criteria("ramls/request.json");
        idCrit.addField("'id'");
        idCrit.setOperation("=");
        idCrit.setValue(request.getId());
        Criterion criterion = new Criterion(idCrit);

        pgClient.update(REQUEST_TABLE, request, criterion, true, updateReply -> {
          if(updateReply.failed()) {
            logger.info(String.format("Error updating request %s: %s", request.getId(),
              updateReply.cause().getLocalizedMessage()));
            future.fail(updateReply.cause());
          } else {
            future.complete();
          }
        });
      } catch(Exception e) {
        future.tryFail(e);
      }
    });
    return future;
  }
}
