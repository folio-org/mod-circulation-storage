package org.folio.support;

import java.util.Map;

import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class PgClientFutureAdapter {

  public static PgClientFutureAdapter create(Context vertxContext, Map<String, String> okapiHeaders) {
    return new PgClientFutureAdapter(PgUtil.postgresClient(vertxContext, okapiHeaders));
  }

  private final PostgresClient client;

  public PgClientFutureAdapter(PostgresClient client) {
    this.client = client;
  }

  public Future<RowSet<Row>> select(String sql) {
    final Promise<RowSet<Row>> promise = Promise.promise();
    client.selectRead(sql, 0, promise);
    return promise.future();
  }

  public Future<RowSet<Row>> execute(String sql) {
    Promise<RowSet<Row>> promise = Promise.promise();
    client.execute(sql, promise);
    return promise.future();
  }
}
