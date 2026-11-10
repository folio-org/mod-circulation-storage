package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.BulkRenewalJob;
import org.folio.rest.jaxrs.resource.BulkRenewalJobStorage;
import org.folio.service.BulkRenewalJobService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class BulkRenewalJobAPI implements BulkRenewalJobStorage {

  @Override
  public void postBulkRenewalJobStorageBulkRenewalJobs(BulkRenewalJob entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new BulkRenewalJobService(vertxContext, okapiHeaders)
      .create(entity)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void getBulkRenewalJobStorageBulkRenewalJobs(String totalRecords, int offset, int limit,
    String query, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new BulkRenewalJobService(vertxContext, okapiHeaders)
      .getAll(offset, limit, query)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void getBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobId(String bulkRenewalJobId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new BulkRenewalJobService(vertxContext, okapiHeaders)
      .findById(bulkRenewalJobId)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void putBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobId(String bulkRenewalJobId,
    BulkRenewalJob entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new BulkRenewalJobService(vertxContext, okapiHeaders)
      .update(bulkRenewalJobId, entity)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void deleteBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobId(String bulkRenewalJobId,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new BulkRenewalJobService(vertxContext, okapiHeaders)
      .delete(bulkRenewalJobId)
      .onComplete(asyncResultHandler);
  }
}
