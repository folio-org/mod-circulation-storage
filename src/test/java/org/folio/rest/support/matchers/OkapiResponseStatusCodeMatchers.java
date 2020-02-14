package org.folio.rest.support.matchers;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.CoreMatchers.is;

import org.folio.rest.support.TextResponse;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class OkapiResponseStatusCodeMatchers {

  /** HTTP Status-Code 200: Ok. */
  public static Matcher<Integer> isOk() {
    return is(HTTP_OK.toInt());
  }

  /** HTTP Status-Code 201: Created. */
  public static Matcher<Integer> isCreated() {
    return is(HTTP_CREATED.toInt());
  }

  /** HTTP Status-Code 204: No content. */
  public static Matcher<Integer> isNoContent() {
    return is(HTTP_NO_CONTENT.toInt());
  }

  /** HTTP Status-Code 400: Bad request. */
  public static Matcher<Integer> isBadRequest() {
    return is(HTTP_BAD_REQUEST.toInt());
  }

  /** HTTP Status-Code 404: Not Found. */
  public static Matcher<Integer> isNotFound() {
    return is(HTTP_NOT_FOUND.toInt());
  }

  /** HTTP Status-Code 422: Unprocessable entity. */
  public static Matcher<Integer> isUnprocessableEntity() {
    return is(HTTP_UNPROCESSABLE_ENTITY.toInt());
  }

  /** HTTP Status-Code 500: Internal server error. */
  public static Matcher<Integer> isInternalServerError() {
    return is(HTTP_INTERNAL_SERVER_ERROR.toInt());
  }

  /** HTTP Status-Code 200: Ok. */
  public static TypeSafeDiagnosingMatcher<TextResponse> matchesOk() {
    return hasStatusCode(HTTP_OK.toInt());
  }

  /** HTTP Status-Code 201: Created. */
  public static TypeSafeDiagnosingMatcher<TextResponse> matchesCreated() {
    return hasStatusCode(HTTP_CREATED.toInt());
  }

  /** HTTP Status-Code 204: No content. */
  public static TypeSafeDiagnosingMatcher<TextResponse> matchesNoContent() {
    return hasStatusCode(HTTP_NO_CONTENT.toInt());
  }

  /** HTTP Status-Code 400: Bad request. */
  public static TypeSafeDiagnosingMatcher<TextResponse> matchesBadRequest() {
    return hasStatusCode(HTTP_BAD_REQUEST.toInt());
  }

  /** HTTP Status-Code 404: Not Found. */
  public static TypeSafeDiagnosingMatcher<TextResponse> matchesNotFound() {
    return hasStatusCode(HTTP_NOT_FOUND.toInt());
  }

  /** HTTP Status-Code 422: Unprocessable entity. */
  public static TypeSafeDiagnosingMatcher<TextResponse> matchesUnprocessableEntity() {
    return hasStatusCode(HTTP_UNPROCESSABLE_ENTITY.toInt());
  }

  /** HTTP Status-Code 500: Internal server error. */
  public static TypeSafeDiagnosingMatcher<TextResponse> matchInternalServerError() {
    return hasStatusCode(HTTP_INTERNAL_SERVER_ERROR.toInt());
  }

  private static TypeSafeDiagnosingMatcher<TextResponse> hasStatusCode(Integer statusCode) {
    return new TypeSafeDiagnosingMatcher<TextResponse>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("a response with status code ")
          .appendValue(statusCode);
      }

      @Override
      protected boolean matchesSafely(TextResponse response, Description description) {
        final Matcher<Integer> statusCodeMatcher = is(statusCode);

        statusCodeMatcher.describeMismatch(response.getStatusCode(), description);

        final boolean matches = statusCodeMatcher.matches(response.getStatusCode());

        if(!matches) {
          description.appendText("\nReceived body: ").appendValue(response.getBody());
        }

        return matches;
      }
    };
  }
}
