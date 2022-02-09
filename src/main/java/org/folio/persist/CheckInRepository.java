package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.CHECKIN_CLASS;
import static org.folio.support.ModuleConstants.CHECKIN_TABLE;

import java.util.Map;

import org.folio.rest.jaxrs.model.CheckIn;

import io.vertx.core.Context;

public class CheckInRepository extends AbstractRepository<CheckIn> {

  public CheckInRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), CHECKIN_TABLE, CHECKIN_CLASS);
  }

}