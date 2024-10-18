package org.folio.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
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
import org.folio.rest.tools.utils.MetadataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.support.ModuleConstants.PRINT_EVENTS_TABLE;
import static org.folio.support.ModuleConstants.REQUEST_TABLE;

public class PrintEventsService {

  private static final Logger LOG = LoggerFactory.getLogger(PrintEventsService.class);
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;

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

  private static String requestPrintSyncQueryString = """
      WITH print_counts AS (
        SELECT
          jsonb->>'requestId' AS request_id,
          COUNT(*) AS print_count
        FROM %s
        WHERE jsonb->>'requestId' IN (%s)
        GROUP BY jsonb->>'requestId'
      )
      UPDATE %s
      SET jsonb =
        (jsonb
          || jsonb_build_object(
               'printDetails',
               jsonb_build_object(
                 'printCount', print_counts.print_count,
                 'requesterId', %s,
                 'isPrinted', true,
                 'printEventDate', %s
               )
             )
        )
      FROM print_counts
      WHERE id = print_counts.request_id::uuid;
      """;



  public PrintEventsService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
  }

  public void create(PrintEventsRequest printEventRequest,
                     Handler<AsyncResult<Response>> asyncResultHandler) {
    LOG.info("create:: save print events {}", printEventRequest);
    List<PrintEvent> printEvents =
      printEventRequest.getRequestIds().stream().map(requestId -> {
      PrintEvent event = new PrintEvent();
      event.setRequestId(requestId);
      event.setRequesterId(printEventRequest.getRequesterId());
      event.setRequesterName(printEventRequest.getRequesterName());
      event.setPrintEventDate(printEventRequest.getPrintEventDate());
      return event;
    }).toList();
    try {
      MetadataUtil.populateMetadata(printEvents, okapiHeaders);
    } catch (Exception e) {
      String msg =
        "Cannot populate metadata of printEvents list elements: " + e.getMessage();
      LOG.error(msg, e);
      asyncResultHandler.handle(succeededFuture(PrintEventsStorage.PostPrintEventsStoragePrintEventsEntryResponse.respond500WithTextPlain(msg)));
      return;
    }

    postgresClient.withTrans(conn -> conn.saveBatch(PRINT_EVENTS_TABLE,
          printEvents)
        .compose(printEventsResult -> conn.execute(
          buildRequestSyncQuery(printEventRequest, okapiHeaders)
        ))).onFailure(handler ->
        asyncResultHandler.handle(
          succeededFuture(PrintEventsStorage.PostPrintEventsStoragePrintEventsEntryResponse.respond500WithTextPlain(handler.getMessage()))
        )
      ).onSuccess(handler ->
        asyncResultHandler.handle(
          succeededFuture(PrintEventsStorage.PostPrintEventsStoragePrintEventsEntryResponse.respond201())
        ));
  }

  private String buildRequestSyncQuery(PrintEventsRequest printEventRequest,
                                       Map<String, String> okapiHeaders) {
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    df.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));

    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    String printEventTableName =
      convertToPsqlStandard(tenantId) + "." + PRINT_EVENTS_TABLE;
    String requestTableName =
      convertToPsqlStandard(tenantId) + "." + REQUEST_TABLE;
    String requestIds = printEventRequest.getRequestIds().stream()
      .map(requestId -> "'" + requestId + "'")
      .collect(Collectors.joining(", "));
    String requesterId = "'" + printEventRequest.getRequesterId() + "'";
    String printEventDate =
      "'" +  df.format(printEventRequest.getPrintEventDate()) + "'";

    return requestPrintSyncQueryString.formatted(printEventTableName,
      requestIds, requestTableName, requesterId,
      printEventDate);
  }

  public void getPrintEventRequestDetails(List<String> requestIds, Handler<AsyncResult<Response>> asyncResultHandler) {
    LOG.debug("getPrintEventRequestDetails:: Fetching print event details for requestIds {}", requestIds);
    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    postgresClient.execute(formatQuery(tenantId, requestIds), handler -> {
      try {
        if (handler.succeeded()) {
          asyncResultHandler.handle(
            succeededFuture(PrintEventsStorage.PostPrintEventsStoragePrintEventsStatusResponse
              .respond200WithApplicationJson(mapRowSetToResponse(handler.result()))));
        } else {
          LOG.warn("getPrintEventRequestDetails:: Error while executing query", handler.cause());
          asyncResultHandler.handle(succeededFuture(PrintEventsStorage.PostPrintEventsStoragePrintEventsStatusResponse
            .respond500WithTextPlain(handler.cause())));
        }
      } catch (Exception ex) {
        LOG.warn("getPrintEventRequestDetails:: Error while fetching print details", ex);
        asyncResultHandler.handle(succeededFuture(PrintEventsStorage.PostPrintEventsStoragePrintEventsEntryResponse
          .respond500WithTextPlain(ex.getMessage())));
      }
    });
  }

  private String formatQuery(String tenantId, List<String> requestIds) {
    String formattedRequestIds = requestIds
      .stream()
      .map(requestId -> "'" + requestId + "'")
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
