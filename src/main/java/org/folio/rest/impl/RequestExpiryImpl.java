
package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.ScheduledRequestExpiration.ScheduledRequestExpirationResponse.respond204;
import static org.folio.rest.jaxrs.resource.ScheduledRequestExpiration.ScheduledRequestExpirationResponse.respond500WithTextPlain;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.ScheduledRequestExpiration;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.support.RequestExpirationService;

public class RequestExpiryImpl implements ScheduledRequestExpiration {

  @Validate
  @Override
  public void expireRequests(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    context.runOnContext(v -> new RequestExpirationService(okapiHeaders, context.owner())
      .doRequestExpirationForTenant()
      .onComplete(result -> {
        if (result.succeeded()) {
          asyncResultHandler.handle(succeededFuture(respond204()));
        } else {
          asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(
            result.cause().getMessage())));
        }
      })
    );
  }
}
