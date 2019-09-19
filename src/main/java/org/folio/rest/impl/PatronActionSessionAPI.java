package org.folio.rest.impl;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import org.folio.cql2pgjson.exception.QueryValidationException;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.PatronActionSession;
import org.folio.rest.jaxrs.model.PatronActionSessions;
import org.folio.rest.jaxrs.resource.PatronActionSessionStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.support.PgClientFutureAdapter;

public class PatronActionSessionAPI implements PatronActionSessionStorage {

  private static final String PATRON_ACTION_SESSION_TABLE = "patron_action_session";
  private static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
  private static final Logger LOGGER = LoggerFactory.getLogger(PatronActionSessionStorage.class);

  @Override
  public void getPatronActionSessionStoragePatronActionSessions(int offset,
    int limit, String query, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.get(PATRON_ACTION_SESSION_TABLE, PatronActionSession.class, PatronActionSessions.class,
      query, offset, limit, okapiHeaders, vertxContext,
      GetPatronActionSessionStoragePatronActionSessionsResponse.class, asyncResultHandler);
  }

  @Override
  public void postPatronActionSessionStoragePatronActionSessions(
    String lang, PatronActionSession entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.post(PATRON_ACTION_SESSION_TABLE, entity, okapiHeaders, vertxContext,
      PostPatronActionSessionStoragePatronActionSessionsResponse.class, asyncResultHandler);
  }

  @Override
  public void getPatronActionSessionStoragePatronActionSessionsByPatronSessionId(
    String patronSessionId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(PATRON_ACTION_SESSION_TABLE, PatronActionSession.class, patronSessionId,
      okapiHeaders, vertxContext, GetPatronActionSessionStoragePatronActionSessionsByPatronSessionIdResponse.class,
      asyncResultHandler);
  }

  @Override
  public void deletePatronActionSessionStoragePatronActionSessionsByPatronSessionId(
    String patronSessionId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.deleteById(PATRON_ACTION_SESSION_TABLE, patronSessionId, okapiHeaders, vertxContext,
      DeletePatronActionSessionStoragePatronActionSessionsByPatronSessionIdResponse.class, asyncResultHandler);
  }

  @Override
  public void getPatronActionSessionStorageExpiredSessionPatronIds(
    String actionType, String lastTimeActionLimit, int limit, Map<String,
    String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    PgClientFutureAdapter pgClient = PgClientFutureAdapter.create(vertxContext, okapiHeaders);

    String sql = toSelectExpiredSessionsQuery(tenantId, PatronActionSession.ActionType.fromValue(actionType),
      limit, DateTime.parse(lastTimeActionLimit));

    pgClient.select(sql)
      .map(this::mapPatronIdResponse)
      .map(GetPatronActionSessionStorageExpiredSessionPatronIdsResponse::respond200WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .setHandler(asyncResultHandler);
  }

  private List<String> mapPatronIdResponse(ResultSet resultSet) {
    return resultSet.getRows()
      .stream()
      .map(json -> json.getString("patron_id"))
      .collect(Collectors.toList());
  }

  @Override
  public void putPatronActionSessionStoragePatronActionSessionsByPatronSessionId(
    String patronSessionId, String lang, PatronActionSession entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.put(PATRON_ACTION_SESSION_TABLE, entity, patronSessionId, okapiHeaders, vertxContext,
      PutPatronActionSessionStoragePatronActionSessionsByPatronSessionIdResponse.class, asyncResultHandler);
  }

  private String toSelectExpiredSessionsQuery(String tenant, PatronActionSession.ActionType actionType,
                                                      int limit, DateTime lastActionDateLimit){

    String tableName = String.format("%s.%s", convertToPsqlStandard(tenant), PATRON_ACTION_SESSION_TABLE);
    String limitDate = lastActionDateLimit.toString(ISODateTimeFormat.dateTime());

    return String.format("SELECT jsonb ->> 'patronId' AS \"patron_id\" " +
      "FROM %s " +
      "WHERE jsonb ->> 'actionType' = '%s' " +
      "GROUP BY jsonb ->> 'patronId' " +
      "HAVING max(jsonb #>> '{metadata,createdDate}') < '%s' " +
      "LIMIT '%d'", tableName, actionType, limitDate, limit);
  }

  private Response mapExceptionToResponse(Throwable t) {

    LOGGER.error(t.getMessage(), t);

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
