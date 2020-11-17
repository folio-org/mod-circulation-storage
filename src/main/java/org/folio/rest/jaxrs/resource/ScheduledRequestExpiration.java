package org.folio.rest.jaxrs.resource;

import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/scheduled-request-expiration")
public interface ScheduledRequestExpiration {

  @POST
  @Produces("text/plain")
  void expireRequests(Map<String, String> okapiHeaders,
                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext);

  class ScheduledRequestExpirationResponse extends ResponseDelegate {
    private ScheduledRequestExpirationResponse(Response response, Object entity) {
      super(response, entity);
    }

    private ScheduledRequestExpirationResponse(Response response) {
      super(response);
    }

    public static ScheduledRequestExpirationResponse respond204() {
      Response.ResponseBuilder responseBuilder = Response.status(204);
      return new ScheduledRequestExpirationResponse(responseBuilder.build());
    }

    public static ScheduledRequestExpirationResponse respond500WithTextPlain(String reason) {
      Response.ResponseBuilder responseBuilder = Response.status(500).header(CONTENT_TYPE, TEXT_PLAIN);
      responseBuilder.entity(reason);

      return new ScheduledRequestExpirationResponse(responseBuilder.build(), reason);
    }
  }
}
