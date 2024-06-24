package org.folio.service;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PrintEvent;
import org.folio.rest.jaxrs.resource.PrintEventsStorage;
import org.folio.rest.persist.PgUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static org.folio.support.ModuleConstants.PRINT_EVENTS_TABLE;

public class BatchPrintEventService {

  private static final Logger LOG = LoggerFactory.getLogger(BatchPrintEventService.class);
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;


  public BatchPrintEventService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
  }

  public Future<Response> create(List<PrintEvent> printEvents) {
    LOG.info("create:: save print events {}", printEvents);
    return PgUtil.postSync(PRINT_EVENTS_TABLE,printEvents,10000,true,okapiHeaders, vertxContext,
      PrintEventsStorage.PostPrintEventsStoragePrintEventsResponse.class);
  }
}
