package org.folio.rest.support.matchers;

import static org.hamcrest.CoreMatchers.startsWith;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import io.vertx.core.json.JsonObject;

public class RequestMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> isOpen() {
    return hasStatus("Open");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isClosed() {
    return hasStatus("Closed");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isAnonymized() {
    return doesNotHaveRequesterId();
  }

  static TypeSafeDiagnosingMatcher<JsonObject> hasStatus(String status) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Request should have a status of ")
            .appendText(status);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
          Description description) {
        if (!representation.containsKey("status")) {
          description.appendText("has no status property");
          return false;
        }

        final Matcher<String> statusMatcher = startsWith(status);

        final String statusName = representation.getString("status");

        statusMatcher.describeMismatch(statusName, description);

        return statusMatcher.matches(statusName);
      }
    };
  }

  static TypeSafeDiagnosingMatcher<JsonObject> doesNotHaveRequesterId() {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Anonymized Request should not have a requesterId");
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
          Description description) {
        return !representation.containsKey("requesterId");
      }
    };
  }
}
