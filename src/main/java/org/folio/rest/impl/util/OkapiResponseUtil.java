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

  /**
   * Parse an Okapi HTTP Response for error messages.
   *
   * This will handle only 4xx (client side) HTTP error messages.
   *
   * @param reply
   *   The Okapi Response to parse for error messages.
   *
   * @return
   *   A string of concatenated error messages associated with the reply.
   *   A newline is appended at the end of each distinct error message.
   */
  public static String getErrorMessage(AsyncResult<Response> reply) {
    String message = null;

    if (reply.succeeded() && reply.result().getStatus() >= 400
      && reply.result().getStatus() < 500 && reply.result().hasEntity()) {

      // When entity is an instance of Errors, the getEntity().toString()
      // will return an object identifier. Process the object to correctly
      // parse the message.
      if (reply.result().getEntity() instanceof Errors) {
        Errors errors = (Errors) reply.result().getEntity();

        if (errors.getErrors().size() > 0)
          message = "";

        for (int i = 0; i < errors.getErrors().size(); i++) {
          Error error = errors.getErrors().get(i);
          message += error.getMessage() + "\n";
        }
      } else {
        message = reply.result().getEntity().toString() + "\n";
      }
    }

    return message;
  }

  /**
   * Search for the given substring within an Okapi HTTP Response.
   *
   * This will handle only 4xx (client side) HTTP error messages.
   *
   * @param reply
   *   The Okapi Response to parse for error messages.
   * @param subString
   *   The substring to find within the error messages.
   *
   * @return
   *   TRUE is returned on match, FALSE otherwise.
   */
  public static boolean containsErrorMessage(AsyncResult<Response> reply, String subString) {
    String message = getErrorMessage(reply);
    return message != null && message.contains(subString);
  }
}
