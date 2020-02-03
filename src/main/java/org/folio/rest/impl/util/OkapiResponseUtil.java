package org.folio.rest.impl.util;

import io.vertx.core.AsyncResult;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

/**
 * Utility methods for Okapi Responses.
 */
public class OkapiResponseUtil {

  private OkapiResponseUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static String getErrorMessage(AsyncResult<Response> reply) {
    String message = null;

    if (reply.succeeded() && reply.result().getStatus() >= 400
      && reply.result().getStatus() < 500 && reply.result().hasEntity()) {

      // When entity is an instance of Errors, the getEntity().toString()
      // will return an object identifier. Process the object to correctly
      // parse the message.
      if (reply.result().getEntity() instanceof Errors) {
        Errors errors = (Errors) reply.result().getEntity();

        for (int i = 0; i < errors.getErrors().size(); i++) {
          Error error = errors.getErrors().get(i);
          message = error.getMessage().toLowerCase();
        }
      } else {
        message = reply.result().getEntity().toString().toLowerCase();
      }
    }

    return message;
  }

  public static boolean containsErrorMessage(AsyncResult<Response> reply, String subString) {
    String message = getErrorMessage(reply);
    return message != null && message.contains(subString);
  }
}
