package org.folio.rest.support.matchers;

import static org.hamcrest.CoreMatchers.is;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import io.vertx.core.json.JsonObject;

public class LoanMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> isOpen() {
    return hasStatus("Open");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isClosed() {
    return hasStatus("Closed");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isAnonymized() {
    return doesNotHaveUserId();
  }

  static TypeSafeDiagnosingMatcher<JsonObject> hasStatus(String status) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override public void describeTo(Description description) {
        description.appendText("Loan should have a status of ")
          .appendText(status);
      }

      @Override protected boolean matchesSafely(JsonObject representation,
        Description description) {
        if (!representation.containsKey("status")) {
          description.appendText("has no status property");
          return false;
        }

        final Matcher<String> statusMatcher = is(status);

        final String statusName = representation
          .getJsonObject("status")
          .getString("name");

        statusMatcher.describeMismatch(statusName, description);

        return statusMatcher.matches(statusName);
      }
    };
  }

  static TypeSafeDiagnosingMatcher<JsonObject> doesNotHaveUserId() {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override public void describeTo(Description description) {
        description.appendText("Anonymized loan should not have a userId");
      }
      @Override protected boolean matchesSafely(JsonObject representation,
        Description description) {
        return !representation.containsKey("userId");
      }
    };
  }
}
