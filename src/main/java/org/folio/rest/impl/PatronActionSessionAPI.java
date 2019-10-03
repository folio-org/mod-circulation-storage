package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
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
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.ExpiredSession;
import org.folio.rest.jaxrs.model.PatronActionExpiredIdsResponse;
import org.folio.rest.jaxrs.model.PatronActionSession;
import org.folio.rest.jaxrs.model.PatronActionSessions;
import org.folio.rest.jaxrs.resource.PatronActionSessionStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.support.PgClientFutureAdapter;

public class PatronActionSessionAPI implements PatronActionSessionStorage {

  private static final String PATRON_ACTION_SESSION_TABLE = "patron_action_session";
  private static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
  private static final Logger LOGGER = LoggerFactory.getLogger(PatronActionSessionAPI.class);
  private static final String PATRON_ID = "patronId";
  private static final String ACTION_TYPE = "actionType";

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

  @Validate
  @Override
  public void getPatronActionSessionStorageExpiredSessionPatronIds(
    String actionType, String sessionInactivityTimeLimit, int limit, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    PgClientFutureAdapter pgClient = PgClientFutureAdapter.create(vertxContext, okapiHeaders);
    DateTime dateTimeLimit;
    try {
      dateTimeLimit = DateTime.parse(sessionInactivityTimeLimit);
    } catch (Exception e) {
      Errors errors = ValidationHelper.createValidationErrorMessage("session_inactivity_time_limit",
        sessionInactivityTimeLimit, "Date cannot be parsed");
      asyncResultHandler.handle(succeededFuture(
        PatronActionSessionStorage.PutPatronActionSessionStoragePatronActionSessionsByPatronSessionIdResponse
          .respond422WithApplicationJson(errors)));
      return;
    }

    PatronActionSession.ActionType mappedActionType = null;
    if (!StringUtils.isBlank(actionType)) {
      try {
        mappedActionType = PatronActionSession.ActionType.fromValue(actionType);
      } catch (IllegalArgumentException e) {
        Errors errors = ValidationHelper.createValidationErrorMessage("action_type",
          sessionInactivityTimeLimit, "Invalid action type value");
        asyncResultHandler.handle(succeededFuture(
          PatronActionSessionStorage.PutPatronActionSessionStoragePatronActionSessionsByPatronSessionIdResponse
            .respond422WithApplicationJson(errors)));
        return;
      }
    }

    String sql = toSelectExpiredSessionsQuery(tenantId, mappedActionType,
      limit, dateTimeLimit);

    pgClient.select(sql)
      .map(this::mapPatronIdResponse)
      .map(GetPatronActionSessionStorageExpiredSessionPatronIdsResponse::respond200WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .setHandler(asyncResultHandler);
  }

  private PatronActionExpiredIdsResponse mapPatronIdResponse(ResultSet resultSet) {
    List<ExpiredSession> expiredSessions = resultSet.getRows()
      .stream()
      .map(this::mapToExpiredSession)
      .collect(Collectors.toList());
    return new PatronActionExpiredIdsResponse().withExpiredSessions(expiredSessions);
  }

  private ExpiredSession mapToExpiredSession(JsonObject jsonObject){
    return new ExpiredSession()
      .withPatronId(jsonObject.getString(PATRON_ID))
      .withActionType(ExpiredSession.ActionType.fromValue(jsonObject.getString(ACTION_TYPE)));
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

    String actionTypeFilter = actionType != null
      ? String.format("WHERE jsonb ->> 'actionType' = '%s'", actionType)
      : "";
    String tableName = String.format("%s.%s", convertToPsqlStandard(tenant), PATRON_ACTION_SESSION_TABLE);
    String limitDate = lastActionDateLimit.toString(ISODateTimeFormat.dateTime());

    return String.format("SELECT jsonb ->> 'patronId' AS \"%s\", " +
      "jsonb ->> 'actionType' AS \"%s\" " +
      "FROM %s %s " +
      "GROUP BY jsonb ->> 'patronId', jsonb ->> 'actionType' " +
      "HAVING max(jsonb #>> '{metadata,createdDate}') < '%s' " +
      "ORDER BY max(jsonb #>> '{metadata,createdDate}') ASC " +
      "LIMIT '%d'", PATRON_ID, ACTION_TYPE, tableName, actionTypeFilter, limitDate, limit);
  }

  private Response mapExceptionToResponse(Throwable t) {

    LOGGER.error(t.getMessage(), t);

    return Response.status(500)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(INTERNAL_SERVER_ERROR)
      .build();
  }
}
