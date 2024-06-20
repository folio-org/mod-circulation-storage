package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.PrintEvent;
import org.folio.rest.jaxrs.resource.PrintEventsStorage;
import org.folio.service.BatchPrintEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.Map;

public class PrintEventsApi implements PrintEventsStorage {
  private static final Logger LOG = LoggerFactory.getLogger(PrintEventsApi.class);
  @Override
  public void postPrintEventsStoragePrintEvents(String lang, PrintEvent printEvent, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
   LOG.info("inside post api ");
    new BatchPrintEventService(vertxContext, okapiHeaders)
      .create(printEvent)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void getPrintEventsStoragePrintEvents(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

  }
}



