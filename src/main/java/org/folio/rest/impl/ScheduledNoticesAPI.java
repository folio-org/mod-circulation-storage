package org.folio.rest.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.cql2pgjson.exception.QueryValidationException;
import org.folio.cql2pgjson.model.SqlSelect;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.NoticeGroup;
import org.folio.rest.jaxrs.model.ScheduledNotice;
import org.folio.rest.jaxrs.model.ScheduledNoticeGroups;
import org.folio.rest.jaxrs.model.ScheduledNotices;
import org.folio.rest.jaxrs.resource.ScheduledNoticeStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.support.PgClientFutureAdapter;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;

public class ScheduledNoticesAPI implements ScheduledNoticeStorage {

  private static final Logger logger = LoggerFactory.getLogger(ScheduledNoticeStorage.class);

  private static final String SCHEDULED_NOTICE_TABLE = "scheduled_notice";
  private static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
  private static final String SCHEDULED_NOTICES = "scheduledNotices";

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

  @Override
  public void deleteScheduledNoticeStorageScheduledNotices(String query,
                                                           Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {


    PgClientFutureAdapter pgClient = PgClientFutureAdapter.create(vertxContext, okapiHeaders);

      cqlToSqlDeleteQuery(query, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT))
        .compose(pgClient::execute)
        .map(v -> DeleteScheduledNoticeStorageScheduledNoticesResponse.respond204())
        .map(Response.class::cast)
        .otherwise(this::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
  }

  @Override
  public void postScheduledNoticeStorageScheduledNotices(String lang,
                                                         ScheduledNotice entity,
                                                         Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {

    PgUtil.post(SCHEDULED_NOTICE_TABLE, entity, okapiHeaders, vertxContext,
      PostScheduledNoticeStorageScheduledNoticesResponse.class, asyncResultHandler);
  }

  @Override
  public void getScheduledNoticeStorageScheduledNoticesByScheduledNoticeId(String scheduledNoticeId,
                                                                           String lang,
                                                                           Map<String, String> okapiHeaders,
                                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                                           Context vertxContext) {

    PgUtil.getById(SCHEDULED_NOTICE_TABLE, ScheduledNotice.class, scheduledNoticeId, okapiHeaders, vertxContext,
      GetScheduledNoticeStorageScheduledNoticesByScheduledNoticeIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteScheduledNoticeStorageScheduledNoticesByScheduledNoticeId(String scheduledNoticeId,
                                                                              String lang, Map<String, String> okapiHeaders,
                                                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                                                              Context vertxContext) {

    PgUtil.deleteById(SCHEDULED_NOTICE_TABLE, scheduledNoticeId, okapiHeaders, vertxContext,
      DeleteScheduledNoticeStorageScheduledNoticesByScheduledNoticeIdResponse.class, asyncResultHandler);

  }

  @Override
  public void getScheduledNoticeStorageScheduledNoticeGroups(String query,
                                                             int groupSizeLimit,
                                                             int limit,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {

    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    PgClientFutureAdapter pgClient = PgClientFutureAdapter.create(vertxContext, okapiHeaders);
    toSelectNoticeGroupsQuery(query, tenantId, groupSizeLimit, limit)
      .compose(pgClient::select)
      .map(this::mapGroupedNoticesResponse)
      .map(GetScheduledNoticeStorageScheduledNoticeGroupsResponse::respond200WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .setHandler(asyncResultHandler);
  }

  private Future<String> toSelectNoticeGroupsQuery(String cql, String tenant, int groupSizeLimit, int limit) {
    String tableName = String.format("%s.%s", convertToPsqlStandard(tenant), SCHEDULED_NOTICE_TABLE);
    Future<String> whereFuture = StringUtils.isBlank(cql)
      ? succeededFuture("")
      : cqlToSql(cql).map(SqlSelect::getWhere).map(where -> "WHERE " + where);
    return whereFuture.map(where -> format(
      "WITH notice_groups AS (\n" +
        "SELECT\n" +
        "jsonb #>> '{userId}' AS \"userId\",\n" +
        "jsonb #>> '{noticeConfig,templateId}' AS \"templateId\",\n" +
        "jsonb #>> '{triggeringEvent}' AS \"triggeringEvent\",\n" +
        "jsonb #>> '{noticeConfig,timing}' AS \"timing\",\n" +
        "jsonb #>> '{noticeConfig,format}' AS \"format\",\n" +
        "COUNT(*) AS \"totalRecords\"\n" +
        "FROM %1$s\n" +
        "%2$s\n" +
        "GROUP BY \n" +
        "jsonb #>> '{userId}',\n" +
        "    jsonb #>> '{noticeConfig,templateId}',\n" +
        "    jsonb #>> '{triggeringEvent}',\n" +
        "    jsonb #>> '{noticeConfig,timing}',\n" +
        "    jsonb #>> '{noticeConfig,format}'\n" +
        "LIMIT %4$d\n" +
        ")\n" +
        "SELECT notice_groups.*, (\n" +
        "SELECT jsonb_agg(jsonb) FROM (\n" +
        "SELECT jsonb FROM %1$s\n" +
        "WHERE jsonb #>> '{userId}' = notice_groups.\"userId\"\n" +
        "    AND jsonb #>> '{noticeConfig,templateId}' = notice_groups.\"templateId\"\n" +
        "    AND jsonb #>> '{triggeringEvent}' = notice_groups.\"triggeringEvent\"\n" +
        "    AND jsonb #>> '{noticeConfig,timing}' = notice_groups.\"timing\"\n" +
        "    AND jsonb #>> '{noticeConfig,format}' = notice_groups.\"format\"\n" +
        "LIMIT %3$d\n" +
        ") AS sn\n" +
        ") AS \"" + SCHEDULED_NOTICES + "\" from notice_groups",
      tableName, where, groupSizeLimit, limit));
  }

  private Future<SqlSelect> cqlToSql(String cql) {
    try {
      return succeededFuture(new CQL2PgJSON("jsonb").toSql(cql));
    } catch (FieldException | QueryValidationException e) {
      return failedFuture(e);
    }
  }

  private ScheduledNoticeGroups mapGroupedNoticesResponse(ResultSet resultSet) {
    List<NoticeGroup> noticeGroups = resultSet.getRows()
      .stream()
      .map(ScheduledNoticesAPI::mapScheduledNoticesToJsonArray)
      .map(json -> json.mapTo(NoticeGroup.class))
      .collect(Collectors.toList());
    return new ScheduledNoticeGroups().withNoticeGroups(noticeGroups);
  }

  private static JsonObject mapScheduledNoticesToJsonArray(JsonObject json) {
    String scheduledNoticesValue = json.getString(SCHEDULED_NOTICES);
    JsonArray scheduledNotices = scheduledNoticesValue == null
      ? new JsonArray() : new JsonArray(scheduledNoticesValue);
    return json.put(SCHEDULED_NOTICES, scheduledNotices);
  }

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
      return cqlToSql(cql)
        .map(SqlSelect::toString)
        .map(where -> sql + " " + where);
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
