package org.folio.rest.impl;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.service.PubSubRegistrationService;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LogManager.getLogger();
  public static final String REFERENCE_KEY = "loadReference";
  public static final String REFERENCE_LEAD = "ref-data";
  public static final String SAMPLE_KEY = "loadSample";
  public static final String SAMPLE_LEAD = "sample-data";

  @Validate
  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
      Map<String, String> headers, Context vertxContext) {
    return super.loadData(attributes, tenantId, headers, vertxContext)
        .compose(superRecordsLoaded -> {
          log.info("Initializing of tenant's data");
          TenantLoading tl = new TenantLoading();
          tl.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD)
              .withIdContent()
              .add("loan-policy-storage/loan-policies")
              .add("request-policy-storage/request-policies")
              .add("patron-notice-policy-storage/patron-notice-policies")
              .add("staff-slips-storage/staff-slips")
              .withIdRaw()
              .add("circulation-rules-storage")
              .withIdContent()
              .add("cancellation-reason-storage/cancellation-reasons")
              .withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD)
              .add("loans", "loan-storage/loans")
              .add("requests", "request-storage/requests");

          return tl.perform(attributes, headers, vertxContext, superRecordsLoaded);
        });
  }

/*  @Validate
  @Override
  public void postTenant(TenantAttributes ta, Map<String, String> headers,
    Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");
    Vertx vertx = cntxt.owner();

    super.postTenant(ta, headers, res -> {
      if (res.failed()) {
        hndlr.handle(res);
        return;
      }
      TenantLoading tl = new TenantLoading();
      tl.withKey("loadReference").withLead("ref-data")
        .withIdContent()
        .add("loan-policy-storage/loan-policies")
        .add("request-policy-storage/request-policies")
        .add("patron-notice-policy-storage/patron-notice-policies")
        .add("staff-slips-storage/staff-slips")
        .withIdRaw()
        .add("circulation-rules-storage")
        .withIdContent()
        .add("cancellation-reason-storage/cancellation-reasons")
        .withKey("loadSample").withLead("sample-data")
        .add("loans", "loan-storage/loans")
        .add("requests", "request-storage/requests")
        .perform(ta, headers, vertx, res1 -> {
          if (res1.failed()) {
            hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
              .respond500WithTextPlain(res1.cause().getLocalizedMessage())));
            return;
          }
          PubSubRegistrationService.registerModule(headers, vertx)
          .whenComplete((aBoolean, throwable) ->
            hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
              .respond201WithApplicationJson(null, PostTenantResponse.headersFor201()))));
        });
    }, cntxt);
  }*/

/*
  @Override
  public void deleteTenant(Map<String, String> headers, Handler<AsyncResult<Response>> handlers, Context cntx) {
    PubSubRegistrationService.unregisterModule(headers, cntx.owner())
      .thenRun(() -> super.deleteTenant(headers, handlers, cntx));
  }
*/
}
