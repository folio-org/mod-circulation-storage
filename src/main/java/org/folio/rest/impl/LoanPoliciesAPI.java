package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LoanPolicies;
import org.folio.rest.jaxrs.model.LoanPolicy;
import org.folio.rest.jaxrs.resource.LoanPolicyStorage;
import org.folio.rest.persist.MyPgUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import javax.ws.rs.core.Response;
import java.util.Map;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class LoanPoliciesAPI implements LoanPolicyStorage {
  private static final String LOAN_POLICY_TABLE = "loan_policy";
  private static final Class<LoanPolicy> LOAN_POLICY_CLASS = LoanPolicy.class;

  @Override
  @Validate
  public void deleteLoanPolicyStorageLoanPolicies(
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("TRUNCATE TABLE %s_%s.%s",
          tenantId, "mod_circulation_storage", LOAN_POLICY_TABLE),
          reply -> asyncResultHandler.handle(Future.succeededFuture(
            DeleteLoanPolicyStorageLoanPoliciesResponse.respond204())));
      }
      catch(Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          LoanPolicyStorage.DeleteLoanPolicyStorageLoanPoliciesResponse
            .respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  @Override
  @Validate
  public void getLoanPolicyStorageLoanPolicies(
    int offset,
    int limit,
    String query,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.get(LOAN_POLICY_TABLE,  LOAN_POLICY_CLASS, LoanPolicies.class, query, offset, limit,
        okapiHeaders, vertxContext,
        GetLoanPolicyStorageLoanPoliciesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postLoanPolicyStorageLoanPolicies(
    String lang, LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    validate(entity, asyncResultHandler, () ->
      PgUtil.post(LOAN_POLICY_TABLE, entity, okapiHeaders, vertxContext,
          PostLoanPolicyStorageLoanPoliciesResponse.class, asyncResultHandler));
  }

  @Override
  @Validate
  public void getLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(LOAN_POLICY_TABLE, LOAN_POLICY_CLASS, loanPolicyId, okapiHeaders, vertxContext,
        GetLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(LOAN_POLICY_TABLE, loanPolicyId, okapiHeaders, vertxContext,
        DeleteLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putLoanPolicyStorageLoanPoliciesByLoanPolicyId(
    String loanPolicyId,
    String lang,
    LoanPolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    validate(entity, asyncResultHandler, () ->
      MyPgUtil.putUpsert204(LOAN_POLICY_TABLE, entity, loanPolicyId, okapiHeaders, vertxContext,
          PutLoanPolicyStorageLoanPoliciesByLoanPolicyIdResponse.class, asyncResultHandler));
  }

  private void respond422(String field, String value, String message, Handler<AsyncResult<Response>> asyncResultHandler) {
    Errors errors = ValidationHelper.createValidationErrorMessage(field, "" + value, message);
    Response response = Response.status(422).header("Content-Type", "application/json").entity(errors).build();
    asyncResultHandler.handle(Future.succeededFuture(response));
  }

  /**
   * @return true if bool is TRUE, false if bool is FALSE or null
   */
  private boolean isTrue(Boolean bool) {
    return Boolean.TRUE.equals(bool);
  }

  void validate(LoanPolicy loanPolicy, Handler<AsyncResult<Response>> asyncResultHandler, Runnable runIfValid) {
    final boolean isFixed = loanPolicy.getLoansPolicy() != null &&
                            loanPolicy.getLoansPolicy().getProfileId() != null &&
                            loanPolicy.getLoansPolicy().getProfileId().equalsIgnoreCase("Fixed");

    // alternate fixed due date
    if ((is(loanPolicy.getRenewable()) &&
         loanPolicy.getRenewalsPolicy() != null &&
         is(loanPolicy.getRenewalsPolicy().getDifferentPeriod()) &&
         isFixed &&
         loanPolicy.getRenewalsPolicy().getAlternateFixedDueDateScheduleId() == null
        )
        ||
        ( loanPolicy.getRenewalsPolicy() != null
          &&
          ( ! is(loanPolicy.getRenewable()) ||
            ! is(loanPolicy.getRenewalsPolicy().getDifferentPeriod())
          )
          && loanPolicy.getRenewalsPolicy().getAlternateFixedDueDateScheduleId() != null
       )) {
      String message =
          "Alternate fixed due date cannot be " + loanPolicy.getRenewalsPolicy().getAlternateFixedDueDateScheduleId() +
          " if renewable is " + loanPolicy.getRenewable() +
          ", different period is " + loanPolicy.getRenewalsPolicy().getDifferentPeriod() +
          " and profile is " + loanPolicy.getLoansPolicy().getProfileId();
      respond422("alternateFixedDueDateScheduleId",
          loanPolicy.getRenewalsPolicy().getAlternateFixedDueDateScheduleId(),
          message, asyncResultHandler);
      return;
    }

    // fixed profile id
    if ( (is(loanPolicy.getLoanable()) &&
          isFixed &&
          loanPolicy.getLoansPolicy().getFixedDueDateScheduleId() == null
         )
         ||
         // TODO: consider adjusting the message
         (! is(loanPolicy.getLoanable()) &&
          loanPolicy.getLoansPolicy() != null &&
          // TODO: consider removing this last condition
          loanPolicy.getLoansPolicy().getFixedDueDateScheduleId() == null
         )
        ) {
      String message = "Fixed due date cannot be null if loanable is " +
        loanPolicy.getLoanable() + " and profile is of type fixed";
      respond422("fixedDueDateScheduleId",
          loanPolicy.getLoansPolicy().getFixedDueDateScheduleId(),
          message, asyncResultHandler);
      return;
    }

    // alternate renewal loan period
    if (isFixed
        && is(loanPolicy.getRenewable())
        &&    loanPolicy.getRequestManagement() != null
        &&    loanPolicy.getRequestManagement().getHolds() != null
        && is(loanPolicy.getRequestManagement().getHolds().getRenewItemsWithRequest())
        &&    loanPolicy.getRequestManagement().getHolds().getAlternateRenewalLoanPeriod() != null) {
      String message = "Alternate Renewal Loan Period for Holds is not allowed for policies with Fixed profile";
      respond422("alternateRenewalLoanPeriod",
          loanPolicy.getRequestManagement().getHolds().getAlternateRenewalLoanPeriod().toString(),
          message, asyncResultHandler);
      return;
    }

    // renewabls policy period
    if (isFixed
        && is(loanPolicy.getRenewable())
        && loanPolicy.getRenewalsPolicy() != null
        && is(loanPolicy.getRenewalsPolicy().getDifferentPeriod())
        && loanPolicy.getRenewalsPolicy().getPeriod() != null) {
      String message = "Period in RenewalsPolicy is not allowed for policies with Fixed profile";
      respond422("period",
          loanPolicy.getRenewalsPolicy().getPeriod().toString(),
          message, asyncResultHandler);
      return;
    }

    runIfValid.run();
  }
}
