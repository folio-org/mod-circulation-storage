package org.folio.service;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static io.vertx.sqlclient.Tuple.tuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class BatchResourceService {
  private static final Logger LOG = LoggerFactory.getLogger(BatchResourceService.class);
  private static final String WHERE_CLAUSE = "WHERE id = '%s'";

  private final PostgresClient postgresClient;

  public BatchResourceService(PostgresClient postgresClient) {
    this.postgresClient = postgresClient;
  }

  /**
   * Execute batch update in a single transaction, subsequently.
   *
   * @param batchFactories  - Factory to create a batch update chunk.
   * @param onFinishHandler - Callback.
   */
  public Future<Void> executeBatchUpdate(
    List<Function<SQLConnection, Future<RowSet<Row>>>> batchFactories,
    Handler<AsyncResult<Void>> onFinishHandler) {

    Promise<Void> promise = Promise.promise();

    postgresClient.startTx(connectionResult -> {
      if (connectionResult.failed()) {
        LOG.warn("Cannot start transaction", connectionResult.cause());
        onFinishHandler.handle(Future.failedFuture(connectionResult.cause()));
        promise.fail(connectionResult.cause());
        return;
      }

      SQLConnection connection = connectionResult.result();
      Future<RowSet<Row>> lastUpdate = Future.succeededFuture();

      for (Function<SQLConnection, Future<RowSet<Row>>> factory : batchFactories) {
        lastUpdate = lastUpdate.compose(prev -> factory.apply(connection));
      }

      lastUpdate.onComplete(updateResult -> {
        if (updateResult.failed()) {
          LOG.warn("Batch update rejected", updateResult.cause());

          postgresClient.rollbackTx(connectionResult, rollback -> {
            onFinishHandler.handle(Future.failedFuture(updateResult.cause()));
            promise.fail(updateResult.cause());
          });
        } else {
          LOG.debug("Update successful, committing transaction");

          postgresClient.endTx(connectionResult, commitResult -> {
            if (commitResult.succeeded()) {
              onFinishHandler.handle(Future.succeededFuture());
              promise.complete();
            } else {
              LOG.warn("Failed to commit transaction", commitResult.cause());
              onFinishHandler.handle(Future.failedFuture(commitResult.cause()));
              promise.fail(commitResult.cause());
            }
          });
        }
      });
    });

    return promise.future();
  }
  /**
   * Creates update single entity batch function.
   *
   * @param tableName - Table name to update.
   * @param id        - Entity ID.
   * @param entity    - New entity.
   * @param <T>       - Entity type.
   * @return Batch function which consumes SQL connection and returns future
   * with result of the update.
   */
  public <T> Function<SQLConnection, Future<RowSet<Row>>> updateSingleEntityBatchFactory(
    String tableName, String id, T entity) {

    return connection -> {
      final Promise<RowSet<Row>> promise = promise();
      final Future<SQLConnection> connectionResult = succeededFuture(connection);

      LOG.debug("Updating entity {} with id {}", entity, id);

      postgresClient.update(connectionResult, tableName, entity, "jsonb",
        String.format(WHERE_CLAUSE, id), false, promise);

      return promise.future();
    };
  }

  /**
   * Creates execute update query with params batch function.
   *
   * @param query  - SQL UPDATE/INSERT/DELETE query.
   * @param params - SQL query params.
   * @return Batch function which consumes SQL connection and returns future
   * with result of the update.
   */
  public Function<SQLConnection, Future<RowSet<Row>>> queryWithParamsBatchFactory(
    String query, Collection<?> params) {

    return connection -> {
      LOG.debug("Executing SQL [{}], got [{}] parameters", query, params.size());

      final Promise<RowSet<Row>> promise = promise();
      final Future<SQLConnection> connectionResult = succeededFuture(connection);

      postgresClient.execute(connectionResult, query,
        tuple(new ArrayList<>(params)), promise);

      return promise.future();
    };
  }
}
