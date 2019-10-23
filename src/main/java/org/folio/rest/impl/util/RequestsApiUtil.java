package org.folio.rest.impl.util;

import java.util.ArrayList;
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
    return errorMessage != null && errorMessage.contains("request_itemid_position_idx_unique");
  }

  public static Errors samePositionInQueueError(String item, Integer position) {
    Error error = new Error();

    error.withMessage("Cannot have more than one request with the same position in the queue")
      .withAdditionalProperty("itemId", item)
      .withAdditionalProperty("position", position);

    List<Error> errorList = new ArrayList<>();
    errorList.add(error);

    Errors errors = new Errors();
    errors.setErrors(errorList);

    return errors;
  }
}
