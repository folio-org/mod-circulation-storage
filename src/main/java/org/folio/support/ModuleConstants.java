package org.folio.support;

import org.folio.rest.jaxrs.model.ActualCostRecord;
import org.folio.rest.jaxrs.model.CheckIn;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;

public class ModuleConstants {
  public static final String MODULE_NAME = "mod_circulation_storage";
  //TODO: Change loan history table name when can be configured, used to be "loan_history_table"
  public static final String LOAN_HISTORY_TABLE = "audit_loan";
  public static final Class<Loan> LOAN_CLASS = Loan.class;
  public static final String LOAN_TABLE = "loan";
  public static final String OPEN_LOAN_STATUS = "Open";
  public static final String REQUEST_TABLE = "request";
  public static final String CIRCULATION_SETTINGS_TABLE =
    "circulation_settings";
  public static final Class<Request> REQUEST_CLASS = Request.class;
  public static final String CHECKIN_TABLE = "check_in";
  public static final Class<CheckIn> CHECKIN_CLASS = CheckIn.class;
  public static final String TLR_FEATURE_TOGGLE_JOB_TABLE = "tlr_feature_toggle_job";
  public static final String ACTUAL_COST_RECORD_TABLE = "actual_cost_record";
  public static final Class<ActualCostRecord> ACTUAL_COST_RECORD_CLASS = ActualCostRecord.class;
  public static final String TLR_FEATURE_TOGGLE_JOB_STATUS_FIELD = "'status'";
  public static final String REQUEST_STATUS_FIELD = "'status'";
  public static final Class<TlrFeatureToggleJob> TLR_FEATURE_TOGGLE_JOB_CLASS =
    TlrFeatureToggleJob.class;
  public static final String REQUEST_POLICY_TABLE = "request_policy";
  public static final Class<RequestPolicy> REQUEST_POLICY_CLASS = RequestPolicy.class;

  private ModuleConstants(){
  }
}
