package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.REQUEST_CLASS;
import static org.folio.support.ModuleConstants.REQUEST_TABLE;

import java.util.Map;

import io.vertx.core.Context;

import org.folio.rest.jaxrs.model.Request;

public class RequestRepository extends AbstractRepository<Request> {

  public RequestRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), REQUEST_TABLE, REQUEST_CLASS);
  }

}