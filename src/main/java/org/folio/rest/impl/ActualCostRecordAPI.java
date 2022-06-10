package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.ActualCostRecord;
import org.folio.rest.jaxrs.model.ActualCostRecords;
import org.folio.rest.jaxrs.resource.ActualCostRecordStorage;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import static org.folio.support.ModuleConstants.ACTUAL_COST_RECORD_CLASS;
import static org.folio.support.ModuleConstants.ACTUAL_COST_RECORD_TABLE;

public class ActualCostRecordAPI implements ActualCostRecordStorage {

  public void getActualCostRecordStorageActualCostRecords(int offset, int limit, String query,
    String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.get(ACTUAL_COST_RECORD_TABLE, ACTUAL_COST_RECORD_CLASS, ActualCostRecords.class,
      query, offset, limit, okapiHeaders, vertxContext,
      GetActualCostRecordStorageActualCostRecordsResponse.class, asyncResultHandler);
  }

  public void postActualCostRecordStorageActualCostRecords(String lang, ActualCostRecord entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.post(ACTUAL_COST_RECORD_TABLE, entity, okapiHeaders, vertxContext,
      PostActualCostRecordStorageActualCostRecordsResponse.class, asyncResultHandler);
  }

  public void getActualCostRecordStorageActualCostRecordsById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(ACTUAL_COST_RECORD_TABLE, ACTUAL_COST_RECORD_CLASS, id,
      okapiHeaders, vertxContext,
      GetActualCostRecordStorageActualCostRecordsByIdResponse.class, asyncResultHandler);
  }

  public void putActualCostRecordStorageActualCostRecordsById(String id, String lang,
    ActualCostRecord entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.put(ACTUAL_COST_RECORD_TABLE, entity, id, okapiHeaders,
      vertxContext, PutActualCostRecordStorageActualCostRecordsByIdResponse.class,
      asyncResultHandler);
  }

  public void deleteActualCostRecordStorageActualCostRecordsById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(ACTUAL_COST_RECORD_TABLE, id, okapiHeaders, vertxContext,
      DeleteActualCostRecordStorageActualCostRecordsByIdResponse.class, asyncResultHandler);
  }
}
