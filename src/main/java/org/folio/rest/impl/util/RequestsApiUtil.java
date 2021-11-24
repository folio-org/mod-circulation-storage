package org.folio.rest.impl.util;

import java.util.ArrayList;
import java.util.List;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Request;

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

  public static Errors requestStructureIsValid(Request request){
    Request.RequestLevel requestLevel = request.getRequestLevel();
    List<Error> errorList = new ArrayList<>();
    if (requestLevel == Request.RequestLevel.ITEM) {
      if (request.getItemId() == null) {
        Error error = new Error();
        error.withMessage("ItemId in Item level request should not be null");
        errorList.add(error);
      }

      if (request.getHoldingsRecordId() == null){
        Error error = new Error();
        error.withMessage("HoldingsRecordId in Item level request should not be null");
        errorList.add(error);
      }
    } else if (requestLevel == Request.RequestLevel.TITLE &&
      ((request.getItemId() != null && request.getHoldingsRecordId() == null)
      || (request.getItemId() == null && request.getHoldingsRecordId() != null))){
        Error error = new Error();
        error.withMessage("In Title level request, there should be either both itemId and holdingsRecordId, or no such fields at all");
        errorList.add(error);
      }

    Errors errors = new Errors();
    errors.setErrors(errorList);
    return errors;
  }
}
