package org.folio.rest.support.matchers;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.vertx.core.json.JsonObject;

public final class LoanHistoryMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> isAnonymized() {
    return new TypeSafeDiagnosingMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Anonymized loan history should not have a userId");
      }

      @Override
      protected boolean matchesSafely(JsonObject representation, Description description) {
        return !representation.getJsonObject("loan").containsKey("userId");
      }
    };
  }
}
