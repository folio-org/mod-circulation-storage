package org.folio.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.PrintEventsRequest;
import org.folio.rest.jaxrs.model.PrintEventsStatusResponse;
import org.folio.rest.jaxrs.model.PrintEventsStatusResponses;
import org.folio.rest.jaxrs.resource.PrintEventsStorage;
import org.folio.rest.model.PrintEvent;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.support.ModuleConstants.PRINT_EVENTS_TABLE;

public class PrintEventsService {

  private static final Logger LOG = LoggerFactory.getLogger(PrintEventsService.class);
  private static final int MAX_ENTITIES = 10000;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;

  private static final String PRINT_EVENT_FETCH_QUERY = """
    WITH cte AS (
        SELECT id, jsonb->>'requestId' AS request_id, jsonb->>'printEventDate' AS last_updated_date,
            jsonb->>'requesterName' AS requester_name, jsonb->>'requesterId' AS requester_id,
            COUNT(*) OVER (PARTITION BY jsonb->>'requestId') AS request_count,
            ROW_NUMBER() OVER (PARTITION BY jsonb->>'requestId'
              ORDER BY (jsonb->>'printEventDate')::timestamptz DESC) AS rank
        FROM %s.%s
        where jsonb->>'requestId' in (%s)
    )
    SELECT request_id, requester_name, requester_id, request_count, (last_updated_date)::timestamptz
    FROM cte
    WHERE
        rank = 1;
    """;



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
      event.setRequesterName(printEventRequest.getRequesterName());
      event.setPrintEventDate(printEventRequest.getPrintEventDate());
      return event;
    }).toList();
    return PgUtil.postSync(PRINT_EVENTS_TABLE, printEvents, MAX_ENTITIES, false, okapiHeaders, vertxContext,
      PrintEventsStorage.PostPrintEventsStoragePrintEventsEntryResponse.class);
  }

  public void getPrintEventRequestDetails(List<String> requestIds, Handler<AsyncResult<Response>> asyncResultHandler) {
    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    postgresClient.execute(formatQuery(tenantId, requestIds), handler -> {
      if(handler.succeeded()) {
        asyncResultHandler.handle(
          succeededFuture(PrintEventsStorage.PostPrintEventsStoragePrintEventsStatusResponse
            .respond200WithApplicationJson(mapRowSetToResponse(handler.result()))));
      } else {
        asyncResultHandler.handle(succeededFuture(PrintEventsStorage.PostPrintEventsStoragePrintEventsStatusResponse
          .respond500WithTextPlain(handler.cause())));
      }
    });
  }

  private String formatQuery(String tenantId, List<String> requestIds) {
    String formattedRequestIds = requestIds.stream().map(requestId -> "'" + requestId + "'")
      .collect(Collectors.joining(", "));
    return String.format(PRINT_EVENT_FETCH_QUERY, convertToPsqlStandard(tenantId), PRINT_EVENTS_TABLE, formattedRequestIds);
  }

  private PrintEventsStatusResponses mapRowSetToResponse(RowSet<Row> rowSet) {
    PrintEventsStatusResponses printEventsStatusResponses = new PrintEventsStatusResponses();
    List<PrintEventsStatusResponse> responseList = new ArrayList<>();
    rowSet.forEach(row -> {
      var response = new PrintEventsStatusResponse();
      response.setRequestId(row.getString("request_id"));
      response.setRequesterName(row.getString("requester_name"));
      response.setRequesterId(row.getString("requester_id"));
      response.setCount(row.getInteger("request_count"));
      response.setPrintEventDate(Date.from(row.getLocalDateTime("last_updated_date")
        .atZone(ZoneOffset.UTC).toInstant()));
      responseList.add(response);
    });
    printEventsStatusResponses.setPrintEventsStatusResponses(responseList);
    printEventsStatusResponses.setTotalRecords(rowSet.size());
    return printEventsStatusResponses;
  }
}
