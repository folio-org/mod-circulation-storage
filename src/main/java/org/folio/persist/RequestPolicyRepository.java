package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.REQUEST_POLICY_CLASS;
import static org.folio.support.ModuleConstants.REQUEST_POLICY_TABLE;

import java.util.Map;

import org.folio.rest.jaxrs.model.RequestPolicy;

import io.vertx.core.Context;

public class RequestPolicyRepository extends AbstractRepository<RequestPolicy> {

  public RequestPolicyRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), REQUEST_POLICY_TABLE, REQUEST_POLICY_CLASS);
  }

}