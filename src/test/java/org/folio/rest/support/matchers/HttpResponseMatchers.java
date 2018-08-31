package org.folio.rest.support.matchers;

import static org.hamcrest.CoreMatchers.is;

import java.net.HttpURLConnection;

import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.TextResponse;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class HttpResponseMatchers {
  public static TypeSafeDiagnosingMatcher<TextResponse> isNotFound() {
    return hasStatusCode(HttpURLConnection.HTTP_NOT_FOUND);
  }

  static TypeSafeDiagnosingMatcher<TextResponse> isUnprocessableEntity() {
    return hasStatusCode(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY);
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
