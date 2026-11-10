package org.folio.service;

import static org.folio.support.ModuleConstants.BULK_RENEWAL_JOB_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.BulkRenewalJob;
import org.folio.rest.jaxrs.model.BulkRenewalJobs;
import org.folio.rest.jaxrs.resource.BulkRenewalJobStorage.DeleteBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobIdResponse;
import org.folio.rest.jaxrs.resource.BulkRenewalJobStorage.GetBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobIdResponse;
import org.folio.rest.jaxrs.resource.BulkRenewalJobStorage.GetBulkRenewalJobStorageBulkRenewalJobsResponse;
import org.folio.rest.jaxrs.resource.BulkRenewalJobStorage.PostBulkRenewalJobStorageBulkRenewalJobsResponse;
import org.folio.rest.jaxrs.resource.BulkRenewalJobStorage.PutBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobIdResponse;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class BulkRenewalJobService {

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;

  public BulkRenewalJobService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
  }

  public Future<Response> create(BulkRenewalJob job) {
    return PgUtil.post(BULK_RENEWAL_JOB_TABLE, job, okapiHeaders, vertxContext,
      PostBulkRenewalJobStorageBulkRenewalJobsResponse.class);
  }

  public Future<Response> getAll(int offset, int limit, String query) {
    return PgUtil.get(BULK_RENEWAL_JOB_TABLE, BulkRenewalJob.class, BulkRenewalJobs.class,
      query, offset, limit, okapiHeaders, vertxContext,
      GetBulkRenewalJobStorageBulkRenewalJobsResponse.class);
  }

  public Future<Response> findById(String id) {
    return PgUtil.getById(BULK_RENEWAL_JOB_TABLE, BulkRenewalJob.class, id, okapiHeaders,
      vertxContext, GetBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobIdResponse.class);
  }

  public Future<Response> update(String id, BulkRenewalJob job) {
    return PgUtil.put(BULK_RENEWAL_JOB_TABLE, job, id, okapiHeaders, vertxContext,
      PutBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobIdResponse.class);
  }

  public Future<Response> delete(String id) {
    return PgUtil.deleteById(BULK_RENEWAL_JOB_TABLE, id, okapiHeaders, vertxContext,
      DeleteBulkRenewalJobStorageBulkRenewalJobsByBulkRenewalJobIdResponse.class);
  }
}
