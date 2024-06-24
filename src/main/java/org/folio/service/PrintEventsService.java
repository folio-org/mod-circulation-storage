package org.folio.service;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PrintEvent;
import org.folio.rest.jaxrs.model.PrintEventsRequest;
import org.folio.rest.jaxrs.resource.PrintEventsStorage;
import org.folio.rest.persist.PgUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static org.folio.support.ModuleConstants.PRINT_EVENTS_TABLE;

public class PrintEventsService {

  private static final Logger LOG = LoggerFactory.getLogger(PrintEventsService.class);
  private static final int MAX_ENTITIES = 10000;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;


  public PrintEventsService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
  }

  public Future<Response> create(PrintEventsRequest printEventRequest) {
    LOG.info("create:: save print events {}", printEventRequest);
    List<PrintEvent> printEvents = printEventRequest.getRequestIds().stream().map(requestId -> {
      PrintEvent event = new PrintEvent();
      event.setRequestId(requestId);
      event.setRequesterId(printEventRequest.getRequesterId());
      event.setPrintEventDate(printEventRequest.getPrintEventDate());
      return event;
    }).toList();
    return PgUtil.postSync(PRINT_EVENTS_TABLE, printEvents, MAX_ENTITIES, false, okapiHeaders, vertxContext,
      PrintEventsStorage.PostPrintEventsStoragePrintEventsResponse.class);
  }
}
