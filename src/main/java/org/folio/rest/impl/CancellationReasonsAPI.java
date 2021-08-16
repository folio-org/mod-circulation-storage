package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CancellationReason;
import org.folio.rest.jaxrs.model.CancellationReasons;
import org.folio.rest.jaxrs.resource.CancellationReasonStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;

import javax.ws.rs.core.Response;
import java.util.Map;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

/**
 *
 * @author kurt
 */
public class CancellationReasonsAPI implements CancellationReasonStorage {

  private static final Logger logger = LogManager.getLogger();
  private static final String TABLE_NAME = "cancellation_reason";
  private boolean suppressErrorResponse = false;

  private String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  private String getErrorResponse(String response) {
    if(suppressErrorResponse) {
      return "Internal Server Error: Please contact Admin";
    }
    return response;
  }

  @Override
  @Validate
  public void deleteCancellationReasonStorageCancellationReasons(String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    try {
      String tenantId = okapiHeaders.get(TENANT_HEADER);
      String deleteAllQuery = String.format("DELETE FROM %s_%s.%s", tenantId,
          ModuleName.getModuleName(), TABLE_NAME);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).execute(deleteAllQuery,
          mutateReply -> {
        if(mutateReply.failed()) {
          String message = logAndSaveError(mutateReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
              DeleteCancellationReasonStorageCancellationReasonsResponse
              .respond500WithTextPlain(getErrorResponse(message))));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
              DeleteCancellationReasonStorageCancellationReasonsResponse
              .noContent().build()));
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteCancellationReasonStorageCancellationReasonsResponse
          .respond500WithTextPlain(getErrorResponse(message))));
    }
  }

  @Override
  @Validate
  public void getCancellationReasonStorageCancellationReasons(int offset,
      int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TABLE_NAME, CancellationReason.class, CancellationReasons.class,
        query, offset, limit, okapiHeaders, vertxContext,
        GetCancellationReasonStorageCancellationReasonsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postCancellationReasonStorageCancellationReasons(String lang,
      CancellationReason entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(TABLE_NAME, entity, okapiHeaders, vertxContext,
        PostCancellationReasonStorageCancellationReasonsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getCancellationReasonStorageCancellationReasonsByCancellationReasonId(
      String cancellationReasonId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TABLE_NAME, CancellationReason.class, cancellationReasonId, okapiHeaders, vertxContext,
        GetCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteCancellationReasonStorageCancellationReasonsByCancellationReasonId(
      String cancellationReasonId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(TABLE_NAME, cancellationReasonId, okapiHeaders, vertxContext,
        DeleteCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putCancellationReasonStorageCancellationReasonsByCancellationReasonId(
      String cancellationReasonId, String lang, CancellationReason entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    PgUtil.put(TABLE_NAME, entity, cancellationReasonId, okapiHeaders, vertxContext,
        PutCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse.class, asyncResultHandler);
  }
}
