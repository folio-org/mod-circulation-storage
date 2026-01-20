package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.support.ModuleConstants.LOAN_HISTORY_TABLE;
import static org.folio.support.ModuleConstants.MODULE_NAME;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AnonymizeStorageLoansRequest;
import org.folio.rest.jaxrs.model.AnonymizeStorageLoansResponse;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.NotAnonymizedLoan;
import org.folio.rest.jaxrs.resource.AnonymizeStorageLoans;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.support.UUIDValidation;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import jakarta.validation.constraints.NotNull;

public class AnonymizeStorageLoansAPI implements AnonymizeStorageLoans {
  private static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  public void postAnonymizeStorageLoans(AnonymizeStorageLoansRequest request,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> responseHandler, Context vertxContext) {

    AnonymizeStorageLoansResponse response = new AnonymizeStorageLoansResponse();
    List<String> loanIds = request.getLoanIds();

    Map<Boolean, List<String>> loanIdsMap = loanIds.stream()
      .collect(Collectors.groupingBy(UUIDValidation::isValidUUID));

    List<String> validIds = loanIdsMap.get(true);
    List<String> invalidIds = loanIdsMap.get(false);

    if (CollectionUtils.isNotEmpty(invalidIds)) {
      log.warn("Invalid loan UUIDs provided: ", invalidIds);
      addToNotAnonimizedLoans(response, "invalidLoanIds", invalidIds);
    }

    if (CollectionUtils.isEmpty(validIds)) {
      final Errors errors = ValidationHelper.createValidationErrorMessage(
        "loanIds", loanIds.toString(), "Please provide valid loanIds");
      responseHandler.handle(succeededFuture(
        PostAnonymizeStorageLoansResponse.respond422WithApplicationJson(errors)));
      return;
    }

    log.info("Anonymizing loans: ", validIds);

    final String tenantId = TenantTool.tenantId(okapiHeaders);
    final PostgresClient postgresClient = PgUtil.postgresClient(vertxContext,
      okapiHeaders);

    final String combinedAnonymizationSql = createAnonymizationSQL(validIds,
      tenantId);
    log.info(String.format("Anonymization SQL: %s", combinedAnonymizationSql));

    executeSql(postgresClient, combinedAnonymizationSql).map(
      updateResult -> PostAnonymizeStorageLoansResponse.respond200WithApplicationJson(
        response.withAnonymizedLoans(validIds)))
      .map(Response.class::cast)
      .otherwise(
        e -> PostAnonymizeStorageLoansResponse.respond500WithTextPlain(e.getMessage()))
      .onComplete(responseHandler);

  }

  private void addToNotAnonimizedLoans(AnonymizeStorageLoansResponse response,
    String reason, List<String> ids) {
    List<NotAnonymizedLoan> notAnonimizedLoans =
      response.getNotAnonymizedLoans();
    notAnonimizedLoans.add(
      new NotAnonymizedLoan().withReason(reason).withLoanIds(ids));
  }

  private Future<RowSet<Row>> executeSql(PostgresClient postgresClient, String sql) {
    final Promise<RowSet<Row>> promise = Promise.promise();

    postgresClient.execute(sql, promise::handle);

    return promise.future();
  }

  private String createAnonymizationSQL(@NotNull Collection<String> loanIdList,
    String tenantId) {

    String loanIds = loanIdList.stream()
      .map(s -> "\'" + s + "\'")
      .collect(Collectors.joining(",", "(", ")"));

    final String AnonymizeStorageLoansSql = String.format(
      new StringBuilder().append("UPDATE %s_%s.loan ")
        .append(" SET jsonb = jsonb - 'userId'")
        .append(" WHERE loan.id in ")
        .append(loanIds)
        .append(" AND loan.jsonb->'status'->>'name' = 'Closed'")
        .append(" AND loan.jsonb->'userId' is NOT null")
        .toString(),
      tenantId, MODULE_NAME);

    // Only anonymize the history for loans that are currently closed
    // meaning that we need to refer to loans in this query
    final String AnonymizeStorageLoansActionHistorySql = String.format(
      new StringBuilder().append("UPDATE %s_%s.%s al")
        .append(" SET jsonb = jsonb #- '{loan,userId}'")
        .append(" WHERE jsonb->'loan'->>'id' IN")
        .append(" (SELECT l.jsonb->>'id' FROM %s_%s.loan l")
        .append(" WHERE l.id in ")
        .append(loanIds)
        .append(" AND l.jsonb->'status'->>'name' = 'Closed')")
        .append(" AND  al.jsonb->'loan'->>'userId' is NOT NULL")
        .toString(),
      tenantId, MODULE_NAME, LOAN_HISTORY_TABLE, tenantId, MODULE_NAME);

    // Loan action history needs to go first, as needs to be for specific loans
    return AnonymizeStorageLoansActionHistorySql + "; " + AnonymizeStorageLoansSql;
  }
}
