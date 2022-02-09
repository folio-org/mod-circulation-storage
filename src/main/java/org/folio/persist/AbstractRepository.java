package org.folio.persist;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public abstract class AbstractRepository<T> {

  protected final PostgresClient postgresClient;
  protected final String tableName;
  protected final Class<T> recordType;

  protected AbstractRepository(PostgresClient postgresClient, String tableName,
      Class<T> recordType) {

    this.postgresClient = postgresClient;
    this.tableName = tableName;
    this.recordType = recordType;
  }

  public Future<String> save(String id, T entity) {
    return postgresClient.save(tableName, id, entity);
  }

  public Future<T> getById(String id) {
    return postgresClient.getById(tableName, id, recordType);
  }

  public Future<List<T>> get(Criterion criterion) {
    final Promise<Results<T>> getItemsResult = promise();

    postgresClient.get(tableName, recordType, criterion, false, getItemsResult);

    return getItemsResult.future().map(Results::getResults);
  }

  public Future<List<T>> get(AsyncResult<SQLConnection> connection, Criterion criterion) {
    final Promise<Results<T>> getItemsResult = promise();

    postgresClient.get(connection, tableName, recordType, criterion, false, true, getItemsResult);

    return getItemsResult.future().map(Results::getResults);
  }

  public Future<Map<String, T>> getById(Collection<String> ids) {
    final Promise<Map<String, T>> promise = promise();

    postgresClient.getById(tableName, new JsonArray(new ArrayList<>(ids)), recordType, promise);

    return promise.future();
  }

  public <V> Future<Map<String, T>> getById(Collection<V> records, Function<V, String> mapper) {
    final Set<String> ids = records.stream()
        .map(mapper)
        .collect(Collectors.toSet());

    return getById(ids);
  }

  public Future<RowSet<Row>> update(AsyncResult<SQLConnection> connection, String id, T rec) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.update(connection, tableName, rec, "jsonb",
        format("WHERE id = '%s'", id), false, promise);

    return promise.future();
  }

  public Future<RowSet<Row>> update(SQLConnection connection, String id, T rec) {
    return update(succeededFuture(connection), id, rec);
  }

  public Future<RowSet<Row>> update(String id, T rec) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.update(tableName, rec, id, promise);

    return promise.future();
  }

  public Future<RowSet<Row>> update(List<T> records) {
    final Promise<RowSet<Row>> promise = promise();

    postgresClient.upsertBatch(tableName, records, promise);

    return promise.future();
  }

  public Future<String> upsert(String id, T rec) {
    return postgresClient.upsert(tableName, id, rec);
  }

  public Future<RowSet<Row>> deleteAll() {
    return postgresClient.delete(tableName, new Criterion());
  }

  public Future<RowSet<Row>> deleteById(String id) {
    return postgresClient.delete(tableName, id);
  }

}
