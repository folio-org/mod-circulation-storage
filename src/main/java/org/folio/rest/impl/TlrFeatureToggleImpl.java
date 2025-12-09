package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.TlrFeatureToggleJobStart.PostTlrFeatureToggleJobStartResponse.respond202;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.resource.TlrFeatureToggleJobStart;
import org.folio.service.tlr.TlrFeatureToggleService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class TlrFeatureToggleImpl implements TlrFeatureToggleJobStart {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public void postTlrFeatureToggleJobStart(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(succeededFuture(respond202()));

    vertxContext.owner().executeBlocking(() -> {
      new TlrFeatureToggleService(okapiHeaders, vertxContext)
        .handle()
        .toCompletionStage()
        .toCompletableFuture()
        .get();
      return null;
    }, false)
    .onSuccess(v -> log.info("TLR feature toggle job succeeded"))
    .onFailure(t -> log.error("TLR feature toggle job failed", t));
  }
}
