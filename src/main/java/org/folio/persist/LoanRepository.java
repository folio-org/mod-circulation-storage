package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.LOAN_CLASS;
import static org.folio.support.ModuleConstants.LOAN_TABLE;

import java.util.Map;

import org.folio.rest.jaxrs.model.Loan;

import io.vertx.core.Context;

public class LoanRepository extends AbstractRepository<Loan> {

  public LoanRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), LOAN_TABLE, LOAN_CLASS);
  }

}