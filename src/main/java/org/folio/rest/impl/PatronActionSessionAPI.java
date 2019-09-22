package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import org.folio.rest.jaxrs.model.PatronActionSession;
import org.folio.rest.jaxrs.model.PatronActionSessions;
import org.folio.rest.jaxrs.resource.PatronActionSessionStorage;
import org.folio.rest.persist.PgUtil;

public class PatronActionSessionAPI implements PatronActionSessionStorage {

  private static final String PATRON_ACTION_SESSION_TABLE = "patron_action_session";

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
  public void putPatronActionSessionStoragePatronActionSessionsByPatronSessionId(
    String patronSessionId, String lang, PatronActionSession entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.put(PATRON_ACTION_SESSION_TABLE, entity, patronSessionId, okapiHeaders, vertxContext,
      PutPatronActionSessionStoragePatronActionSessionsByPatronSessionIdResponse.class, asyncResultHandler);
  }
}
