
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

public class RequestExpiryImpl implements ScheduledRequestExpiration {

  @Validate
  @Override
  public void expireRequests(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    context.runOnContext(v -> new ConfigurationClient(context.owner(), okapiHeaders).getTlrSettings()
      .compose(tlrSettings -> createRequestExpirationService(okapiHeaders, context, tlrSettings)
        .doRequestExpiration()
      .onComplete(result -> {
        if (result.succeeded()) {
          asyncResultHandler.handle(succeededFuture(respond204()));
        } else {
          asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(
            result.cause().getMessage())));
        }
      })
    ));
  }

  private RequestExpirationService createRequestExpirationService(Map<String, String> okapiHeaders,
    Context context, TlrSettingsConfiguration tlrSettings) {

    return tlrSettings.isTitleLevelRequestsFeatureEnabled()
      ? new RequestExpirationService(okapiHeaders, context.owner(), "instanceId",
        Request::getInstanceId)
      : new RequestExpirationService(okapiHeaders, context.owner(), "itemId",
        Request::getItemId);
  }
}
