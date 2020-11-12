package org.folio.rest.impl;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.service.PubSubRegistrationService;

public class TenantRefAPI extends TenantAPI {
  private static final Logger log = LoggerFactory.getLogger(TenantRefAPI.class);

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
          .handle((aBoolean, throwable) -> {
            hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
              .respond201WithApplicationJson("")));
            return null;
          });
        });
    }, cntxt);
  }

  @Override
  public void deleteTenant(Map<String, String> headers, Handler<AsyncResult<Response>> handlers, Context cntx) {
    PubSubRegistrationService.unregisterModule(headers, cntx.owner())
      .thenRun(() -> super.deleteTenant(headers, handlers, cntx));
  }
}
