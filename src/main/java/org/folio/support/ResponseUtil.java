package org.folio.support;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;

import java.util.Objects;

import javax.ws.rs.core.Response;

import org.folio.HttpStatus;

public final class ResponseUtil {

  private ResponseUtil() {
  }

  public static boolean isUpdateSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_NO_CONTENT);
  }

  public static boolean isDeleteSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_NO_CONTENT);
  }

  public static boolean isCreateSuccessResponse(Response response) {
    return responseHasStatus(response, HTTP_CREATED);
  }

  public static Response createdResponse(Object entity) {
    return Response.status(HTTP_CREATED.toInt())
        .header(CONTENT_TYPE, "application/json")
        .entity(entity).build();
  }

  public static Response noContentResponse() {
    return Response.status(HTTP_NO_CONTENT.toInt()).build();
  }

  public static Response internalErrorResponse(Throwable error) {
    return failedResponse(HTTP_INTERNAL_SERVER_ERROR, error);
  }

  public static Response badRequestResponse(Throwable error) {
    return failedResponse(HTTP_BAD_REQUEST, error);
  }

  private static boolean responseHasStatus(Response response, HttpStatus expectedStatus) {
    return response != null && response.getStatus() == expectedStatus.toInt();
  }

  private static Response failedResponse(HttpStatus status, Throwable error) {
    Objects.requireNonNull(error);

    return Response.status(status.toInt())
        .header(CONTENT_TYPE, "text/plain")
        .entity(error.getMessage()).build();
  }
}
