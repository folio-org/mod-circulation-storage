package org.folio.support;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;

import javax.ws.rs.core.Response;

import lombok.experimental.UtilityClass;

import org.folio.HttpStatus;

@UtilityClass
public class ResponseUtil {

  public boolean isUpdateSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_NO_CONTENT);
  }

  public boolean isDeleteSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_NO_CONTENT);
  }

  public boolean isCreateSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_CREATED);
  }

  private boolean responseHasStatus(Response response, HttpStatus expectedStatus) {
    return response != null && response.getStatus() == expectedStatus.toInt();
  }

}
