package org.folio.support;

import org.folio.rest.jaxrs.model.Loan;

public class ModuleConstants {
  public static final String MODULE_NAME = "mod_circulation_storage";
  //TODO: Change loan history table name when can be configured, used to be "loan_history_table"
  public static final String LOAN_HISTORY_TABLE = "audit_loan";
  public static final Class<Loan> LOAN_CLASS = Loan.class;
  public static final String LOAN_TABLE = "loan";
  public static final String OPEN_LOAN_STATUS = "Open";

  private ModuleConstants(){
  }
}
