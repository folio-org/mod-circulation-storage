
package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.ScheduledRequestExpiration.ScheduledRequestExpirationResponse.respond204;
import static org.folio.rest.jaxrs.resource.ScheduledRequestExpiration.ScheduledRequestExpirationResponse.respond500WithTextPlain;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.client.ConfigurationClient;
import org.folio.rest.configuration.TlrSettingsConfiguration;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.resource.ScheduledRequestExpiration;
import org.folio.service.RequestExpirationService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class RequestExpiryImpl implements ScheduledRequestExpiration {

  @Validate
  @Override
  public void expireRequests(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> handler, Context context) {

    Vertx vertx = context.owner();
    new ConfigurationClient(vertx, okapiHeaders).getTlrSettings()
      .onSuccess(tlrSettings -> doExpiration(tlrSettings, handler, okapiHeaders, vertx))
      .onFailure(t -> doExpiration(new TlrSettingsConfiguration(false, false, null, null, null), 
        handler, okapiHeaders, vertx));
  }

  private void doExpiration(TlrSettingsConfiguration tlrSettings,
    Handler<AsyncResult<Response>> handler, Map<String, String> okapiHeaders, Vertx vertx) {
    
    createRequestExpirationService(okapiHeaders, vertx, tlrSettings)
      .doRequestExpiration()
        .onSuccess(x -> handler.handle(succeededFuture(respond204())))
        .onFailure(e -> handler.handle(succeededFuture(respond500WithTextPlain(e.getMessage()))));
  }

  private RequestExpirationService createRequestExpirationService(Map<String, String> okapiHeaders,
    Vertx vertx, TlrSettingsConfiguration tlrSettings) {

    return tlrSettings.isTitleLevelRequestsFeatureEnabled()
      ? new RequestExpirationService(okapiHeaders, vertx, "instanceId",
        Request::getInstanceId)
      : new RequestExpirationService(okapiHeaders, vertx, "itemId",
        Request::getItemId);
  }
}
