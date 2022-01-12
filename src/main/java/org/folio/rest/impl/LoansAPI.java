package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import static org.folio.rest.impl.Headers.TENANT_HEADER;
import static org.folio.support.ModuleConstants.LOAN_HISTORY_TABLE;
import static org.folio.support.ModuleConstants.MODULE_NAME;

import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.LoansHistoryItem;
import org.folio.rest.jaxrs.model.LoansHistoryItems;
import org.folio.rest.jaxrs.resource.LoanStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.service.loan.LoanService;
import org.folio.support.ResultHandlerFactory;
import org.folio.support.ServerErrorResponder;
import org.folio.support.UUIDValidation;
import org.folio.support.VertxContextRunner;

public class LoansAPI implements LoanStorage {
  private static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  public void deleteLoanStorageLoans(
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("TRUNCATE TABLE %s_%s.loan",
          tenantId, MODULE_NAME),
          reply -> asyncResultHandler.handle(succeededFuture(
            DeleteLoanStorageLoansResponse.respond204())));
      } catch (Exception e) {
        asyncResultHandler.handle(succeededFuture(
          LoanStorage.DeleteLoanStorageLoansResponse
            .respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  @Validate
  @Override
  public void getLoanStorageLoans(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).findByQuery(query, offset, limit)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postLoanStorageLoans(
    String lang,
    Loan loan,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).create(loan)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postLoanStorageLoansAnonymizeByUserId(
    String userId,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> responseHandler,
    Context vertxContext) {

    final ServerErrorResponder serverErrorResponder =
      new ServerErrorResponder(PostLoanStorageLoansAnonymizeByUserIdResponse
        ::respond500WithTextPlain, responseHandler, log);

    final VertxContextRunner runner = new VertxContextRunner(
      vertxContext, serverErrorResponder::withError);

    runner.runOnContext(() -> {
      if (!UUIDValidation.isValidUUID(userId)) {
        final Errors errors = ValidationHelper.createValidationErrorMessage(
          "userId", userId, "Invalid user ID, should be a UUID");

        responseHandler.handle(succeededFuture(
          PostLoanStorageLoansAnonymizeByUserIdResponse
            .respond422WithApplicationJson(errors)));
        return;
      }

      final String tenantId = TenantTool.tenantId(okapiHeaders);

      final PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), tenantId);

      final String combinedAnonymizationSql = createAnonymizationSQL(userId,
        tenantId);

      log.info(String.format("Anonymization SQL: %s", combinedAnonymizationSql));

      postgresClient.execute(combinedAnonymizationSql, ResultHandlerFactory.when(
        s -> responseHandler.handle(succeededFuture(
          PostLoanStorageLoansAnonymizeByUserIdResponse.respond204())),
        serverErrorResponder::withError));
    });
  }

  @Validate
  @Override
  public void getLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).findById(loanId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).delete(loanId)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void putLoanStorageLoansByLoanId(
    String loanId,
    String lang,
    Loan loan,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new LoanService(vertxContext, okapiHeaders).update(loanId, loan)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getLoanStorageLoanHistory(int offset, int limit, String query, String lang,
                                        Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    String cql = query;
    if (StringUtils.isBlank(cql)) {
      cql = "cql.allRecords=1";
    }
    if (!cql.toLowerCase().contains(" sortby ")) {
      cql += " sortBy createdDate/sort.descending";
    }
    PgUtil.get(LOAN_HISTORY_TABLE, LoansHistoryItem.class, LoansHistoryItems.class, cql, offset, limit, okapiHeaders,
      vertxContext, GetLoanStorageLoanHistoryResponse.class, asyncResultHandler);
  }

  private String createAnonymizationSQL(
    @NotNull String userId,
    String tenantId) {

    final String anonymizeLoansSql = String.format(
      "UPDATE %s_%s.loan"
        + " SET jsonb = jsonb - 'userId'"
        + " WHERE jsonb->>'userId' = '" + userId + "'"
        + " AND jsonb->'status'->>'name' = 'Closed'",
      tenantId, MODULE_NAME);

    //Only anonymize the history for loans that are currently closed
    //meaning that we need to refer to loans in this query
    final String anonymizeLoansActionHistorySql = String.format(
      "UPDATE %s_%s.%s"
        + " SET jsonb = jsonb #- '{loan,userId}'"
        + " WHERE jsonb->'loan'->>'id' IN"
        + "   (SELECT l.jsonb->>'id'"
        + "    FROM %s_%s.loan l"
        + "    WHERE l.jsonb->>'userId' = '" + userId + "'"
        + "      AND l.jsonb->'status'->>'name' = 'Closed')",
      tenantId, MODULE_NAME, LOAN_HISTORY_TABLE,
      tenantId, MODULE_NAME);

    //Loan action history needs to go first, as needs to be for specific loans
    return anonymizeLoansActionHistorySql + "; " + anonymizeLoansSql;
  }

}
