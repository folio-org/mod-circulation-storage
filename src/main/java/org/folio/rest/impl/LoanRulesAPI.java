package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.model.LoanRules;
import org.folio.rest.jaxrs.resource.LoanRulesStorage;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.UpdateSection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

public class LoanRulesAPI implements LoanRulesStorage {
  private static final Logger log = LoggerFactory.getLogger(LoanRulesStorage.class);
  private static final String LOAN_RULES_TABLE = "loan_rules";

  private void internalErrorGet(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error(e);
    asyncResultHandler.handle(Future.succeededFuture(
        LoanRulesStorage.GetLoanRulesStorageResponse.
        respond500WithTextPlain(e.getMessage())));
  }

  @Override
  public void getLoanRulesStorage(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
              vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
          postgresClient.get(LOAN_RULES_TABLE, LoanRules.class, "", true, false,
            reply -> {
              try {
                if (reply.failed()) {
                  internalErrorGet(asyncResultHandler, reply.cause());
                  return;
                }

                @SuppressWarnings("unchecked")
                List<LoanRules> loanRulesList = (List<LoanRules>) reply.result().getResults();

                if (loanRulesList.size() != 1) {
                  internalErrorGet(asyncResultHandler, new IllegalStateException("loanRulesList.size() = "
                      + loanRulesList.size()));
                  return;
                }

                LoanRules loanRules = loanRulesList.get(0);
                asyncResultHandler.handle(Future.succeededFuture(
                    LoanRulesStorage.GetLoanRulesStorageResponse.respond200WithApplicationJson(loanRules)));
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
        LoanRulesStorage.PutLoanRulesStorageResponse.
        respond500WithTextPlain(e.getMessage())));
  }

  @Override
  public void putLoanRulesStorage(LoanRules entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
              vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
          UpdateSection updateSection = new UpdateSection().addField("loanRulesAsTextFile");
          updateSection.setValue(entity.getLoanRulesAsTextFile());

          postgresClient.update(LOAN_RULES_TABLE, updateSection, (Criterion)null, true, update -> {
              try {
                if (update.failed()) {
                  internalErrorPut(asyncResultHandler, update.cause());
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(
                    PutLoanRulesStorageResponse.respond204()));
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
