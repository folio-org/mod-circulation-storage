package org.folio.rest.impl.support;

import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;

public class DatabaseIdentity {
  private final String identityFieldName;

  public DatabaseIdentity(String identityFieldName) {
    this.identityFieldName = identityFieldName;
  }

  public Criterion queryByIdentity(String loanId) {
    Criteria a = new Criteria();

    a.addField(identityFieldName);
    a.setOperation("=");
    a.setValue("'" + loanId + "'");
    a.setJSONB(false);

    return new Criterion(a);
  }

  public void configurePostgresClient(PostgresClient client) {
    client.setIdField(identityFieldName);
  }
}
