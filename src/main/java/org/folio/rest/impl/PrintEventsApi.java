package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.PrintEvents;
import org.folio.rest.jaxrs.resource.PrintEventsStorage;
import org.folio.service.BatchPrintEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.vertx.core.Future.succeededFuture;

public class PrintEventsApi implements PrintEventsStorage {
  private static final Logger LOG = LoggerFactory.getLogger(PrintEventsApi.class);
  @Override
  public void postPrintEventsStoragePrintEvents(PrintEvents printEvents, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    LOG.info("inside post api {}", printEvents);
    new BatchPrintEventService(vertxContext, okapiHeaders)
      .create(printEvents.getPrintEvents())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(throwable -> asyncResultHandler.handle(succeededFuture(PostPrintEventsStoragePrintEventsResponse.respond500WithTextPlain(throwable.getMessage()))));
  }
  }




