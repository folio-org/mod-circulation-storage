package org.folio.support;

import org.folio.rest.jaxrs.model.CheckIn;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Request;

public class ModuleConstants {
  public static final String MODULE_NAME = "mod_circulation_storage";
  //TODO: Change loan history table name when can be configured, used to be "loan_history_table"
  public static final String LOAN_HISTORY_TABLE = "audit_loan";
  public static final Class<Loan> LOAN_CLASS = Loan.class;
  public static final String LOAN_TABLE = "loan";
  public static final String OPEN_LOAN_STATUS = "Open";
  public static final String REQUEST_TABLE = "request";
  public static final Class<Request> REQUEST_CLASS = Request.class;
  public static final String CHECKIN_TABLE = "check_in";
  public static final Class<CheckIn> CHECKIN_CLASS = CheckIn.class;

  private ModuleConstants(){
  }
}
