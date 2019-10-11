package org.folio.service;

import static org.folio.rest.jaxrs.model.RequestsBatch.TransactionMode;

import java.util.List;
import java.util.function.Function;

import org.folio.rest.persist.PostgresClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

public class BatchResourceService {
  private static final Logger LOG = LoggerFactory.getLogger(BatchResourceService.class);
  private static final String WHERE_CLAUSE = "WHERE id = '%s'";

  private final PostgresClient postgresClient;
  private final String tableName;

  public BatchResourceService(PostgresClient postgresClient, String tableName) {
    this.postgresClient = postgresClient;
    this.tableName = tableName;
  }

  /**
   * Execute batch update.
   *
   * @param transactionMode - Transaction mode for update:
   *                        Same - use one transaction for all entities
   * @param entities        - Entities to update
   * @param getId           - Function to get ID from an entity.
   * @param onFinishHandler - Callback.
   * @param <T>             - Entity type.
   * @throws UnsupportedOperationException if transaction mode is not supported.
   */
  public <T> void executeBatchUpdate(
    TransactionMode transactionMode, List<T> entities, Function<T, String> getId,
    Handler<AsyncResult<Void>> onFinishHandler) {

    // Currently only one mode supported
    if (transactionMode == TransactionMode.SAME) {
      executeBatchUpdateInSameTransaction(entities, getId, onFinishHandler);
    } else {
      throw new UnsupportedOperationException(
        "Transaction mode: [" + transactionMode + "] is not supported now");
    }
  }

  private <T> void executeBatchUpdateInSameTransaction(
    List<T> entities, Function<T, String> getId, Handler<AsyncResult<Void>> onFinishHandler) {

    postgresClient.startTx(connectionResult -> {
      if (connectionResult.failed()) {
        LOG.warn("Can not start transaction", connectionResult.cause());
        onFinishHandler.handle(Future.failedFuture(connectionResult.cause()));
        return;
      }

      // Using this future for chaining updates
      Future<UpdateResult> lastUpdate = Future.succeededFuture();
      for (T entity : entities) {
        String id = getId.apply(entity);
        lastUpdate = lastUpdate
          .compose(prev -> updateSingleEntity(connectionResult, id, entity));
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
   * Executes an update for a single entity on given DB connection.
   *
   * @param connection - DB connection.
   * @param id         - Entity ID to update.
   * @param entity     - New entity state.
   * @param <T>        - Entity type.
   * @return Callback.
   */
  private <T> Future<UpdateResult> updateSingleEntity(
    AsyncResult<SQLConnection> connection, String id, T entity) {

    Future<UpdateResult> updateResultFuture = Future.future();

    postgresClient.update(connection, tableName, entity, "jsonb",
      String.format(WHERE_CLAUSE, id), false, updateResultFuture);

    return updateResultFuture;
  }
}
