package org.folio.support;


import java.util.Map;

import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;

public class PgClientFutureAdapter {

  public static PgClientFutureAdapter create(Context vertxContext, Map<String, String> okapiHeaders) {
    return new PgClientFutureAdapter(PgUtil.postgresClient(vertxContext, okapiHeaders));
  }

  private final PostgresClient client;

  public PgClientFutureAdapter(PostgresClient client) {
    this.client = client;
  }

  public Future<ResultSet> select(String sql) {
    Future<ResultSet> future = Future.future();
    client.select(sql, future);
    return future;
  }

  public Future<UpdateResult> execute(String sql) {
    Future<UpdateResult> future = Future.future();
    client.execute(sql, future);
    return future;
  }
}
