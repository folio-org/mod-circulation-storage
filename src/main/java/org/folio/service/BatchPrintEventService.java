package org.folio.service;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.persist.PrintEventsRepository;
import org.folio.rest.jaxrs.model.PrintEvent;
import org.folio.rest.jaxrs.resource.PrintEventsStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.rest.tools.utils.ValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.folio.rest.jaxrs.resource.PrintEventsStorage.PostPrintEventsStoragePrintEventsResponse.respond201WithApplicationJson;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.PRINT_EVENTS_TABLE;

public class BatchPrintEventService {

  private static final Logger LOG = LoggerFactory.getLogger(BatchPrintEventService.class);
  private static final int BATCH_SIZE = 100; // Maximum batch size
  private static final long BATCH_INTERVAL = 1500; // Batch interval in milliseconds

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PrintEventsRepository repository;


  //private final ConcurrentLinkedQueue<PrintEvent> eventQueue = new ConcurrentLinkedQueue<>();

  public BatchPrintEventService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    this.repository = new PrintEventsRepository(vertxContext, okapiHeaders);

    vertxContext.owner().setPeriodic(BATCH_INTERVAL, this::processBatch);
  }

  public Future<Response> create(PrintEvent printEvent) {
    LOG.info("inside the create method");
    PrintEvent p1 = new PrintEvent();
    p1.setRequestId("870e5d94-db85-41d6-b206-1bbadace6e6c");
    p1.setRequesterId("824358e8-c32b-43d5-9e13-b1bfc1548270");
    p1.setPrintEventDate(new Date());

    PrintEvent p2 = new PrintEvent();
    p1.setRequestId("026a2783-a974-4ad5-8b13-2ea8313b3445");
    p1.setRequesterId("52d7563a-7c85-4c20-80e8-886532416eb5");
    p1.setPrintEventDate(new Date());

    List<PrintEvent> batch = List.of(p1,p2);
//    Promise<Response> promise = Promise.promise();
//    eventQueue.add(printEvent);
//    promise.complete(Response.status(202).entity("Event received").build());
//    return promise.future();
    return saveBatch(batch);
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

//  private Future<Response> saveBatch(List<PrintEvent> batch) {
//    LOG.info("inside save batch");
//    return PgUtil.postSync(PRINT_EVENTS_TABLE,batch,5,true,okapiHeaders, vertxContext,
//      PrintEventsStorage.PostPrintEventsStoragePrintEventsResponse.class);
//  }
  private Future<Response> saveBatch(List<PrintEvent> entities) {
    LOG.info("inside save batch");
      return postgresClient(vertxContext, okapiHeaders).saveBatch(PRINT_EVENTS_TABLE,entities)
        .<Response>map(x -> Response.status(201).build());
  }

}
