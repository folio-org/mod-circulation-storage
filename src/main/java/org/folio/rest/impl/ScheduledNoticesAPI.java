package org.folio.rest.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.commons.lang3.StringUtils;
import org.folio.cql2pgjson.exception.QueryValidationException;
import org.folio.persist.Cql2PgJsonHolder;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ScheduledNotice;
import org.folio.rest.jaxrs.model.ScheduledNotices;
import org.folio.rest.jaxrs.resource.ScheduledNoticeStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

public class ScheduledNoticesAPI implements ScheduledNoticeStorage {

  private static final Logger logger = LogManager.getLogger();

  private static final String SCHEDULED_NOTICE_TABLE = "scheduled_notice";
  private static final String INTERNAL_SERVER_ERROR = "Internal Server Error";

  @Validate
  @Override
  public void getScheduledNoticeStorageScheduledNotices(int offset,
                                                        int limit,
                                                        String query,
                                                        String lang,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {

    PgUtil.get(SCHEDULED_NOTICE_TABLE, ScheduledNotice.class, ScheduledNotices.class, query, offset, limit,
      okapiHeaders, vertxContext, GetScheduledNoticeStorageScheduledNoticesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteScheduledNoticeStorageScheduledNotices(String query,
                                                           Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {


      PostgresClient pgClient = PgUtil.postgresClient(vertxContext, okapiHeaders);

      cqlToSqlDeleteQuery(query, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT))
        .compose(sql -> pgClient.execute(sql))
        .map(v -> DeleteScheduledNoticeStorageScheduledNoticesResponse.respond204())
        .map(Response.class::cast)
        .otherwise(this::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void postScheduledNoticeStorageScheduledNotices(String lang,
                                                         ScheduledNotice entity,
                                                         Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {

    PgUtil.post(SCHEDULED_NOTICE_TABLE, entity, okapiHeaders, vertxContext,
      PostScheduledNoticeStorageScheduledNoticesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getScheduledNoticeStorageScheduledNoticesByScheduledNoticeId(String scheduledNoticeId,
                                                                           String lang,
                                                                           Map<String, String> okapiHeaders,
                                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                                           Context vertxContext) {

    PgUtil.getById(SCHEDULED_NOTICE_TABLE, ScheduledNotice.class, scheduledNoticeId, okapiHeaders, vertxContext,
      GetScheduledNoticeStorageScheduledNoticesByScheduledNoticeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteScheduledNoticeStorageScheduledNoticesByScheduledNoticeId(String scheduledNoticeId,
                                                                              String lang, Map<String, String> okapiHeaders,
                                                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                                                              Context vertxContext) {

    PgUtil.deleteById(SCHEDULED_NOTICE_TABLE, scheduledNoticeId, okapiHeaders, vertxContext,
      DeleteScheduledNoticeStorageScheduledNoticesByScheduledNoticeIdResponse.class, asyncResultHandler);

  }

  @Validate
  @Override
  public void putScheduledNoticeStorageScheduledNoticesByScheduledNoticeId(String scheduledNoticeId,
                                                                           String lang,
                                                                           ScheduledNotice entity,
                                                                           Map<String, String> okapiHeaders,
                                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                                           Context vertxContext) {

    PgUtil.put(SCHEDULED_NOTICE_TABLE, entity, scheduledNoticeId, okapiHeaders, vertxContext,
      PutScheduledNoticeStorageScheduledNoticesByScheduledNoticeIdResponse.class, asyncResultHandler);

  }

  private Future<String> cqlToSqlDeleteQuery(String cql, String tenant) {

    String sql = format("DELETE FROM %s.%s", convertToPsqlStandard(tenant), SCHEDULED_NOTICE_TABLE);

    if (StringUtils.isEmpty(cql)) {
      return succeededFuture(sql);
    } else {
      return cqlToSqlQuery(cql)
        .map(where -> sql + " WHERE " + where);
    }
  }

  private Future<String> cqlToSqlQuery(String cql) {

    try {
      return succeededFuture(Cql2PgJsonHolder.getCql2PgJson("jsonb").cql2pgJson(cql));
    } catch (QueryValidationException e) {
      return failedFuture(e);
    }
  }

  private Response mapExceptionToResponse(Throwable t) {

    logger.error(t.getMessage(), t);

    if (t.getClass() == QueryValidationException.class) {
      return Response.status(400)
        .header(CONTENT_TYPE, TEXT_PLAIN)
        .entity(t.getMessage())
        .build();
    }

    return Response.status(500)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(INTERNAL_SERVER_ERROR)
      .build();
  }
}
