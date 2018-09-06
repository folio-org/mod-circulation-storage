package org.folio.rest.support.matchers;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.rest.support.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static org.hamcrest.CoreMatchers.is;

import org.folio.rest.support.TextResponse;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class HttpResponseStatusCodeMatchers {
  public static TypeSafeDiagnosingMatcher<TextResponse> isNotFound() {
    return hasStatusCode(HTTP_NOT_FOUND);
  }

  static TypeSafeDiagnosingMatcher<TextResponse> isUnprocessableEntity() {
    return hasStatusCode(UNPROCESSABLE_ENTITY);
  }

  public static TypeSafeDiagnosingMatcher<TextResponse> isNoContent() {
    return hasStatusCode(HTTP_NO_CONTENT);
  }

  public static TypeSafeDiagnosingMatcher<TextResponse> isCreated() {
    return hasStatusCode(HTTP_CREATED);
  }

  public static TypeSafeDiagnosingMatcher<TextResponse> isOk() {
    return hasStatusCode(HTTP_OK);
  }

  public static TypeSafeDiagnosingMatcher<TextResponse> isBadRequest() {
    return hasStatusCode(HTTP_BAD_REQUEST);
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
