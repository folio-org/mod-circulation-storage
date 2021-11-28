package org.folio.rest.impl.util;

import java.util.ArrayList;
import java.util.List;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Request.RequestLevel;

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

  public static Errors validateRequest(Request request){
    RequestLevel requestLevel = request.getRequestLevel();
    List<Error> errorList = new ArrayList<>();
    boolean itemIdIsNotPresent = request.getItemId() == null;
    boolean holdingsRecordIdIsNotPresent = request.getHoldingsRecordId() == null;
    if (requestLevel == RequestLevel.ITEM) {
      if (itemIdIsNotPresent) {
        errorList.add(createError("Item ID in item level request should not be null"));
      }

      if (holdingsRecordIdIsNotPresent){
        errorList.add(createError("Holdings record ID in item level request should not be null"));
      }
    } else if (requestLevel == RequestLevel.TITLE &&
      (itemIdIsNotPresent ^ holdingsRecordIdIsNotPresent)){
        errorList.add(createError(
          "Title level request must have both itemId and holdingsRecordId or neither"));
      }

    return new Errors().withErrors(errorList);
  }

  private static Error createError(String message){
    return new Error().withMessage(message);
  }
}
