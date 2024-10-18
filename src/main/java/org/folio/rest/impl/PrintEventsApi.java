package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.PrintEventsRequest;
import org.folio.rest.jaxrs.model.PrintEventsStatusRequest;
import org.folio.rest.jaxrs.resource.PrintEventsStorage;
import org.folio.service.PrintEventsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.Map;

public class PrintEventsApi implements PrintEventsStorage {
  private static final Logger LOG = LoggerFactory.getLogger(PrintEventsApi.class);

  @Override
  public void postPrintEventsStoragePrintEventsEntry(PrintEventsRequest printEventsRequest, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    LOG.info("postPrintEventsStoragePrintEvents:: save print events {}", printEventsRequest);
    new PrintEventsService(vertxContext, okapiHeaders)
      .create(printEventsRequest, asyncResultHandler);
  }

  @Override
  public void postPrintEventsStoragePrintEventsStatus(PrintEventsStatusRequest entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    LOG.info("postPrintEventsStoragePrintEventsStatus:: Fetching print event details for requestIds {}",
      entity.getRequestIds());
    new PrintEventsService(vertxContext, okapiHeaders)
      .getPrintEventRequestDetails(entity.getRequestIds(), asyncResultHandler);
  }
}
