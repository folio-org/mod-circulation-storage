package org.folio.rest.support.matchers;

import static org.folio.rest.support.matchers.HttpResponseMatchers.isUnprocessableEntity;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasErrorWith;

import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.TextResponse;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.vertx.core.json.JsonObject;

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
        final Matcher<TextResponse> statusCodeMatcher = isUnprocessableEntity();

        if(!statusCodeMatcher.matches(response)) {
          statusCodeMatcher.describeMismatch(response, description);
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
