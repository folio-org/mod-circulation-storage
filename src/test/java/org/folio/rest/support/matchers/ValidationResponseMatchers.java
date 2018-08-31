package org.folio.rest.support.matchers;

import io.vertx.core.json.JsonObject;

import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static org.hamcrest.CoreMatchers.is;

public class ValidationResponseMatchers {
  public static TypeSafeDiagnosingMatcher<JsonResponse> isValidationResponseWhich(
    Matcher<JsonObject> errorMatcher) {

    return new TypeSafeDiagnosingMatcher<JsonResponse>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("A response with status code 422 and an error which ")
          .appendDescriptionOf(errorMatcher);
      }

      @Override
      protected boolean matchesSafely(JsonResponse response, Description description) {
        final Matcher<Integer> statusCodeMatcher = is(422);

        if(!statusCodeMatcher.matches(response.getStatusCode())) {
          statusCodeMatcher.describeMismatch(response.getStatusCode(), description);
          return false;
        }

        final TypeSafeDiagnosingMatcher<JsonObject> validationErrorsMatcher
          = hasErrorWith(errorMatcher);

        final JsonObject body = response.getJson();

        validationErrorsMatcher.describeMismatch(body, description);

        return validationErrorsMatcher.matches(body);
      }
    };
  }
}
