package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.rest.jaxrs.resource.CirculationRulesStorage;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.UpdateSection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

public class CirculationRulesAPI implements CirculationRulesStorage {
  private static final Logger log = LoggerFactory.getLogger(CirculationRulesStorage.class);
  static final String CIRCULATION_RULES_TABLE = "circulation_rules";

  private void internalErrorGet(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error(e);
    asyncResultHandler.handle(Future.succeededFuture(
      CirculationRulesStorage.GetCirculationRulesStorageResponse.
        respond500WithTextPlain(e.getMessage())));
  }

  @Override
  public void getCirculationRulesStorage(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
              vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
          postgresClient.get(CIRCULATION_RULES_TABLE, CirculationRules.class, "", true, false,
            reply -> {
              try {
                if (reply.failed()) {
                  internalErrorGet(asyncResultHandler, reply.cause());
                  return;
                }

                List<CirculationRules> circulationRulesList = reply.result().getResults();

                if (circulationRulesList.size() != 1) {
                  internalErrorGet(asyncResultHandler, new IllegalStateException("circulationRulesList.size() = "
                      + circulationRulesList.size()));
                  return;
                }

                CirculationRules circulationRules = circulationRulesList.get(0);
                asyncResultHandler.handle(Future.succeededFuture(
                  CirculationRulesStorage.GetCirculationRulesStorageResponse.respond200WithApplicationJson(circulationRules)));
              } catch (Exception e) {
                internalErrorGet(asyncResultHandler, e);
              }
            });
        } catch (Exception e) {
          internalErrorGet(asyncResultHandler, e);
        }
      });
    } catch (Exception e) {
      internalErrorGet(asyncResultHandler, e);
    }
  }

  private void internalErrorPut(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error(e);
    asyncResultHandler.handle(Future.succeededFuture(
      CirculationRulesStorage.PutCirculationRulesStorageResponse.
        respond500WithTextPlain(e.getMessage())));
  }

  @Override
  public void putCirculationRulesStorage(CirculationRules entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
              vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
          UpdateSection updateSection = new UpdateSection().addField("rulesAsText");
          updateSection.setValue(entity.getRulesAsText());

          postgresClient.update(CIRCULATION_RULES_TABLE, updateSection, (Criterion)null, true, update -> {
              try {
                if (update.failed()) {
                  internalErrorPut(asyncResultHandler, update.cause());
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(
                    PutCirculationRulesStorageResponse.respond204()));
              } catch (Exception e) {
                internalErrorPut(asyncResultHandler, e);
              }
            });
        } catch (Exception e) {
          internalErrorPut(asyncResultHandler, e);
        }
      });
    } catch (Exception e) {
      internalErrorPut(asyncResultHandler, e);
    }
  }
}
