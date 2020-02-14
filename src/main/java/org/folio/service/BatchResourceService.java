package org.folio.service;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.folio.rest.persist.PostgresClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

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
  public void executeBatchUpdate(
    List<Function<SQLConnection, Future<UpdateResult>>> batchFactories,
    Handler<AsyncResult<Void>> onFinishHandler) {

    postgresClient.startTx(connectionResult -> {
      if (connectionResult.failed()) {
        LOG.warn("Can not start transaction", connectionResult.cause());
        onFinishHandler.handle(Future.failedFuture(connectionResult.cause()));
        return;
      }

      SQLConnection connection = connectionResult.result();

      // Using this future for chaining updates
      Future<UpdateResult> lastUpdate = Future.succeededFuture();
      for (Function<SQLConnection, Future<UpdateResult>> factory : batchFactories) {
        lastUpdate = lastUpdate.compose(prev -> factory.apply(connection));
      }

      // Handle overall update result and decide on whether to commit or rollback transaction
      lastUpdate.setHandler(updateResult -> {
        if (updateResult.failed()) {
          LOG.warn("Batch update rejected", updateResult.cause());

          // Rollback transaction and keep original cause.
          postgresClient.rollbackTx(connectionResult,
            rollback -> onFinishHandler.handle(Future.failedFuture(updateResult.cause()))
          );
        } else {
          LOG.debug("Update successful, committing transaction");

          postgresClient.endTx(connectionResult, onFinishHandler);
        }
      });
    });
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
  public <T> Function<SQLConnection, Future<UpdateResult>> updateSingleEntityBatchFactory(
    String tableName, String id, T entity) {

    return connection -> {
      Promise<UpdateResult> promise = Promise.promise();
      Future<SQLConnection> connectionResult = Future.succeededFuture(connection);

      LOG.debug("Updating entity {} with id {}", entity, id);

      postgresClient.update(connectionResult, tableName, entity, "jsonb",
        String.format(WHERE_CLAUSE, id), false, promise.future());

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
  public Function<SQLConnection, Future<UpdateResult>> queryWithParamsBatchFactory(
    String query, Collection<?> params) {

    return connection -> {
      Promise<UpdateResult> promise = Promise.promise();
      LOG.debug("Executing SQL [{}], got [{}] parameters", query, params.size());

      connection.updateWithParams(query,
        new JsonArray(Lists.newArrayList(params)),
        promise.future()
      );

      return promise.future();
    };
  }
}
