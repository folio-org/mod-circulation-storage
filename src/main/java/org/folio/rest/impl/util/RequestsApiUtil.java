package org.folio.rest.impl.util;

import java.util.List;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

/**
 * Utility methods for Requests resource.
 */
public class RequestsApiUtil {

  private RequestsApiUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static boolean hasSamePositionConstraintViolated(Throwable cause) {
    return cause != null && hasSamePositionConstraintViolated(cause.getMessage());
  }

  public static boolean hasSamePositionConstraintViolated(String errorMessage) {
    return errorMessage != null &&
      (errorMessage.contains("request_itemid_position_idx_unique") ||
      errorMessage.contains("value already exists in table "));
  }

  public static Errors samePositionInQueueError(String item, Integer position) {
    Error error = createError(
      "Cannot have more than one request with the same position in the queue")
      .withAdditionalProperty("itemId", item)
      .withAdditionalProperty("position", position);

    return new Errors().withErrors(List.of(error));
  }

  private static Error createError(String message){
    return new Error().withMessage(message);
  }
}
