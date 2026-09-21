package org.folio.rest.impl.util;

import java.util.ArrayList;
import java.util.List;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Request.RequestLevel;
import org.folio.support.UUIDValidation;

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

  public static Errors validateRequest(Request request) {
    RequestLevel requestLevel = request.getRequestLevel();
    List<Error> errorList = new ArrayList<>();
    boolean isItemIdAbsent = request.getItemId() == null;
    boolean isHoldingsRecordIdAbsent = request.getHoldingsRecordId() == null;
    if (requestLevel == RequestLevel.ITEM) {
      if (isItemIdAbsent) {
        errorList.add(createError("Item ID in item level request should not be absent"));
      }

      if (isHoldingsRecordIdAbsent){
        errorList.add(createError("Holdings record ID in item level request should not be absent"));
      }
    } else if (requestLevel == RequestLevel.TITLE && isWrongFieldsCombination(isItemIdAbsent,
      isHoldingsRecordIdAbsent)) {

      errorList.add(createError(
        "Title level request must have both itemId and holdingsRecordId or neither"));
    }

    if (isOpenAndHasNoRequesterId(request)) {
      errorList.add(createError(
          "Open request must have a requester ID"));
    }
    if (request.getRequesterId() != null &&
        !UUIDValidation.isValidUUID(request.getRequesterId())) {
      errorList.add(createError( "Invalid requester ID, should be a UUID"));
    }

    return new Errors().withErrors(errorList);
  }

  private static boolean isWrongFieldsCombination(boolean isItemIdAbsent,
    boolean isHoldingsRecordIdAbsent) {

    return isItemIdAbsent ^ isHoldingsRecordIdAbsent;
  }

  private static boolean isOpenAndHasNoRequesterId(Request request) {
    return request.getStatus().value().startsWith("Open -")
        && request.getRequesterId() == null;
  }

  private static Error createError(String message){
    return new Error().withMessage(message);
  }
}
