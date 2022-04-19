package org.folio.rest.jaxrs.resource;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

@Path("/tlr-feature-toggle")
public interface TlrFeatureToggle {

  @POST
  @Produces("text/plain")
  void tlrFeatureToggle(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext);

  class TlrFeatureToggleResponse extends ResponseDelegate {
    private TlrFeatureToggleResponse(Response response, Object entity) {
      super(response, entity);
    }

    private TlrFeatureToggleResponse(Response response) {
      super(response);
    }

    public static TlrFeatureToggleResponse respond204() {
      ResponseBuilder responseBuilder = Response.status(204);
      return new TlrFeatureToggleResponse(responseBuilder.build());
    }

    public static TlrFeatureToggleResponse respond422WithApplicationJson(Errors entity) {
      Response.ResponseBuilder responseBuilder = Response.status(422).header("Content-Type",
        "application/json");
      responseBuilder.entity(entity);
      return new TlrFeatureToggleResponse(responseBuilder.build(), entity);
    }

    public static TlrFeatureToggleResponse respond500WithTextPlain(String reason) {
      ResponseBuilder responseBuilder = Response.status(500).header(CONTENT_TYPE, TEXT_PLAIN);
      responseBuilder.entity(reason);

      return new TlrFeatureToggleResponse(responseBuilder.build(), reason);
    }
  }
}
