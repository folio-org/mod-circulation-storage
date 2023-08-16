package org.folio.persist;

import static org.folio.support.ModuleConstants.REQUEST_POLICY_CLASS;
import static org.folio.support.ModuleConstants.REQUEST_POLICY_TABLE;

import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.persist.PostgresClient;

public class RequestPolicyRepository extends AbstractRepository<RequestPolicy> {
  public RequestPolicyRepository(PostgresClient postgresClient) {
    super(postgresClient, REQUEST_POLICY_TABLE, REQUEST_POLICY_CLASS);
  }
}
