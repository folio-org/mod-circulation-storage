package org.folio.service;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PrintEvent;
import org.folio.rest.jaxrs.resource.PrintEventsStorage;
import org.folio.rest.persist.PgUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import static org.folio.support.ModuleConstants.PRINT_EVENTS_TABLE;

public class BatchPrintEventService {

  private static final Logger LOG = LoggerFactory.getLogger(BatchPrintEventService.class);
  private static final int BATCH_SIZE = 100; // Maximum batch size
  private static final long BATCH_INTERVAL = 1500; // Batch interval in milliseconds

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;


  public BatchPrintEventService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
  }

  public Future<Response> create(List<PrintEvent> printEvent) {
    LOG.info("inside create batch");
//    List<PrintEvent> batch = new ArrayList<>();
//    for(int i=0;i<100;i++) {
//      PrintEvent pEvent = new PrintEvent();
//      pEvent.setRequesterId(UUID.randomUUID().toString());
//      pEvent.setRequestId(UUID.randomUUID().toString());
//      pEvent.setPrintEventDate(new Date());
//      batch.add(pEvent);
//    }
    return saveBatch(printEvent);
  }

  private void processBatch(Long timerId) {
//    List<PrintEvent> batch = new ArrayList<>();
//
//    while (!eventQueue.isEmpty() && batch.size() < BATCH_SIZE) {
//      batch.add(eventQueue.poll());
//    }
//
//    if (!batch.isEmpty()) {
//      saveBatch(batch).onComplete(ar -> {
//        if (ar.failed()) {
//          System.err.println("Batch processing failed: " + ar.cause().getMessage());
//        }
//      });
//    }
    LOG.info("process batch execution ");
  }

  private Future<Response> saveBatch(List<PrintEvent> batch) {
    LOG.info("inside save batch");
    return PgUtil.postSync(PRINT_EVENTS_TABLE,batch,100,true,okapiHeaders, vertxContext,
      PrintEventsStorage.PostPrintEventsStoragePrintEventsResponse.class);
  }

}
