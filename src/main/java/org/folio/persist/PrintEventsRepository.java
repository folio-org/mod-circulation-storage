package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.PRINT_EVENTS_TABLE;

import java.util.Map;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.PrintEvent;

public class PrintEventsRepository extends AbstractRepository<PrintEvent>{
  public PrintEventsRepository(Context context, Map<String,
    String> okapiHeaders) {

    super(postgresClient(context, okapiHeaders), PRINT_EVENTS_TABLE,
      PrintEvent.class);
  }

}
