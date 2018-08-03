/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CancellationReason;
import org.folio.rest.jaxrs.model.CancellationReasons;
import org.folio.rest.jaxrs.resource.CancellationReasonStorageResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.utils.OutStream;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.impl.Headers.TENANT_HEADER;

/**
 *
 * @author kurt
 */
public class CancellationReasonsAPI implements CancellationReasonStorageResource {
  
  private static final Logger logger = LoggerFactory.getLogger(CancellationReasonsAPI.class);
  private static final String TABLE_NAME = "cancellation_reason";
  private boolean suppressErrorResponse = false;
  private static final String ID_FIELD = "'id'";
  
  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(TABLE_NAME + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit))
        .setOffset(new Offset(offset));
  }
  
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

  private boolean isDuplicate(String errorMessage){
    return errorMessage != null
      && errorMessage.contains("duplicate key value violates unique constraint");
  }
  
  private boolean isStillReferenced(String errorMessage){
    return errorMessage != null
      && errorMessage.contains("violates foreign key constraint");
  }

  @Override
  public void deleteCancellationReasonStorageCancellationReasons(String lang, 
      Map<String, String> okapiHeaders, 
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    try {
      String tenantId = okapiHeaders.get(TENANT_HEADER);
      String deleteAllQuery = String.format("DELETE FROM %s_%s.%s", tenantId,
          PomReader.INSTANCE.getModuleName(), TABLE_NAME);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).mutate(deleteAllQuery,
          mutateReply -> {
        if(mutateReply.failed()) {
          String message = logAndSaveError(mutateReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
              DeleteCancellationReasonStorageCancellationReasonsResponse
              .withPlainInternalServerError(getErrorResponse(message))));     
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
          .withPlainInternalServerError(getErrorResponse(message))));     
    }
  }

  @Override
  public void getCancellationReasonStorageCancellationReasons(int offset,
      int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    try {
      String tenantId = okapiHeaders.get(TENANT_HEADER);
      CQLWrapper cql = getCQL(query, limit, offset);
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
          .get(TABLE_NAME, CancellationReason.class, new String[]{"*"},
          cql, true, true, getReply -> {
        try {
          if(getReply.failed()) {
            String message = logAndSaveError(getReply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
                GetCancellationReasonStorageCancellationReasonsResponse
                .withPlainInternalServerError(getErrorResponse(message))));              
          } else {
            CancellationReasons collection = new CancellationReasons();
            List<CancellationReason> crList = (List<CancellationReason>) 
                getReply.result().getResults();
            collection.setCancellationReasons(crList);
            collection.setTotalRecords(getReply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
                GetCancellationReasonStorageCancellationReasonsResponse
                .withJsonOK(collection)));               
          }
        } catch(Exception e) {
          String message = logAndSaveError(e);
          asyncResultHandler.handle(Future.succeededFuture(
              GetCancellationReasonStorageCancellationReasonsResponse
              .withPlainInternalServerError(getErrorResponse(message))));          
        }     
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          GetCancellationReasonStorageCancellationReasonsResponse
          .withPlainInternalServerError(getErrorResponse(message))));      
    }
  }

  @Override
  @Validate
  public void postCancellationReasonStorageCancellationReasons(String lang,
      CancellationReason entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    try {
      String tenantId = okapiHeaders.get(TENANT_HEADER);
      String id = entity.getId();
      if(id == null) {
        id = UUID.randomUUID().toString();
        entity.setId(id);
      }
      PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(),
          tenantId);
      pgClient.save(TABLE_NAME, id, entity, reply -> {
        try {
          if(reply.failed()) {
            String message = logAndSaveError(reply.cause());
            if(isDuplicate(message)) {
              asyncResultHandler.handle(Future.succeededFuture(
                  PostCancellationReasonStorageCancellationReasonsResponse
                  .withPlainBadRequest(PgExceptionUtil.badRequestMessage(reply.cause()))));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                  PostCancellationReasonStorageCancellationReasonsResponse
                  .withPlainInternalServerError(getErrorResponse(message))));
            }
          } else {
            OutStream stream = new OutStream();
            stream.setData(entity);
            asyncResultHandler.handle(Future.succeededFuture(
                PostCancellationReasonStorageCancellationReasonsResponse
                .withJsonCreated(reply.result(), stream)));
          }
        } catch(Exception e) {
          String message = logAndSaveError(e);
          asyncResultHandler.handle(Future.succeededFuture(
              PostCancellationReasonStorageCancellationReasonsResponse
              .withPlainInternalServerError(getErrorResponse(message))));
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          PostCancellationReasonStorageCancellationReasonsResponse
          .withPlainInternalServerError(getErrorResponse(message))));
    }
  }

  @Override
  public void getCancellationReasonStorageCancellationReasonsByCancellationReasonId(
      String cancellationReasonId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    try {
      Criteria idCrit = new Criteria().setOperation("=")
          .setValue(cancellationReasonId).addField(ID_FIELD);
      String tenantId = okapiHeaders.get(TENANT_HEADER);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).get(TABLE_NAME,
          CancellationReason.class, new Criterion(idCrit), true, false, getReply -> {
        if(getReply.failed()) {
          String message = logAndSaveError(getReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
              GetCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
              .withPlainInternalServerError(getErrorResponse(message))));
        } else {
          List<CancellationReason> reasons = (List<CancellationReason>) getReply.result().getResults();
          if(reasons.isEmpty()) {
            asyncResultHandler.handle(Future.succeededFuture(
              GetCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                  .withPlainNotFound("No record with that id")));
          } else {
           asyncResultHandler.handle(Future.succeededFuture(
              GetCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                  .withJsonOK(reasons.get(0))));
          }
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          GetCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
          .withPlainInternalServerError(getErrorResponse(message))));
    }
  }

  @Override
  public void deleteCancellationReasonStorageCancellationReasonsByCancellationReasonId(
      String cancellationReasonId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    try {
      Criteria idCrit = new Criteria().setOperation("=")
          .setValue(cancellationReasonId).addField(ID_FIELD);
      String tenantId = okapiHeaders.get(TENANT_HEADER);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(TABLE_NAME,
          new Criterion(idCrit), deleteReply -> {
        if(deleteReply.failed()) {
          String message = logAndSaveError(deleteReply.cause());
          if(isStillReferenced(message)) {
            asyncResultHandler.handle(Future.succeededFuture(DeleteCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                .withPlainBadRequest(PgExceptionUtil.badRequestMessage(deleteReply.cause()))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                DeleteCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                .withPlainInternalServerError(getErrorResponse(message))));
          }
        } else {
          if(deleteReply.result().getUpdated() < 1) {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                  .withPlainNotFound("Record not found")));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                  .withNoContent()));
          }         
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
          .withPlainInternalServerError(getErrorResponse(message))));      
    }
  }

  @Override
  @Validate
  public void putCancellationReasonStorageCancellationReasonsByCancellationReasonId(
      String cancellationReasonId, String lang, CancellationReason entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    try {
      Criteria idCrit = new Criteria().setOperation("=")
          .setValue(cancellationReasonId).addField(ID_FIELD);
      String tenantId = okapiHeaders.get(TENANT_HEADER);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).update(TABLE_NAME, 
          entity, new Criterion(idCrit), true, putHandler -> {            
        if(putHandler.failed()) {
          String message = logAndSaveError(putHandler.cause());
          if(isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
                PutCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                .withPlainBadRequest(PgExceptionUtil.badRequestMessage(putHandler.cause()))));            
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                PutCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                .withPlainInternalServerError(getErrorResponse(message))));
          }
        } else {
          if(putHandler.result().getUpdated() < 1) {
            asyncResultHandler.handle(Future.succeededFuture(
                PutCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                .withPlainNotFound("Record not found")));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                PutCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
                .withNoContent()));
          }
        }
      });
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          PutCancellationReasonStorageCancellationReasonsByCancellationReasonIdResponse
          .withPlainInternalServerError(getErrorResponse(message))));
    }
  }  
}
