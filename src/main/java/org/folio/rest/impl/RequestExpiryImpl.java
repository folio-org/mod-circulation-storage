
package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.ScheduledRequestExpiration.ScheduledRequestExpirationResponse.respond204;
import static org.folio.rest.jaxrs.resource.ScheduledRequestExpiration.ScheduledRequestExpirationResponse.respond500WithTextPlain;
import static org.folio.support.ExpirationTool.doRequestExpiration;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.resource.ScheduledRequestExpiration;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class RequestExpiryImpl implements ScheduledRequestExpiration {

  @Override
  public void expireRequests(Map<String, String> okapiHeaders,
                             Handler<AsyncResult<Response>> asyncResultHandler, Context context) {

    context.runOnContext(v -> doRequestExpiration(okapiHeaders, context.owner())
      .onComplete(result -> {
        if (result.succeeded()) {
          asyncResultHandler.handle(succeededFuture(respond204()));
        } else {
          asyncResultHandler.handle(
            succeededFuture(respond500WithTextPlain(result.cause().getMessage())));
        }
      })
    );
  }
}
