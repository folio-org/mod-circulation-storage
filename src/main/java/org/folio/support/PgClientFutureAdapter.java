package org.folio.support;

import java.util.Map;

import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
    Promise<ResultSet> promise = Promise.promise();
    client.select(sql, promise.future());
    return promise.future();
  }

  public Future<UpdateResult> execute(String sql) {
    Promise<UpdateResult> promise = Promise.promise();
    client.execute(sql, promise.future());
    return promise.future();
  }
}
