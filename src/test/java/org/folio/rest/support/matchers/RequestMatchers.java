package org.folio.rest.support.matchers;

import static org.hamcrest.CoreMatchers.startsWith;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import io.vertx.core.json.JsonObject;

public class RequestMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> isOpen() {
    return hasStatus("Open");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isClosed() {
    return hasStatus("Closed");
  }

  public static Matcher<JsonObject> isAnonymized() {
    return allOf(
        not(hasRequesterId()),
        not(hasRequesterSummary()),
        not(hasProxyUserId()),
        not(hasProxySummary()));
  }

  public static Matcher<JsonObject> isNotAnonymized() {
    return anyOf(
        hasRequesterId(),
        hasRequesterSummary(),
        hasProxyUserId(),
        hasProxySummary());
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

  static TypeSafeDiagnosingMatcher<JsonObject> hasRequesterId() {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("have a requesterId");
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
          Description description) {
        return representation.containsKey("requesterId");
      }
    };
  }

  static TypeSafeDiagnosingMatcher<JsonObject> hasRequesterSummary() {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("have requester summary");
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
          Description description) {
        return representation.containsKey("requester");
      }
    };
  }

  static TypeSafeDiagnosingMatcher<JsonObject> hasProxyUserId() {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("have a proxyUserId");
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
          Description description) {
        return representation.containsKey("proxyUserId");
      }
    };
  }

  static TypeSafeDiagnosingMatcher<JsonObject> hasProxySummary() {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("have proxy summary");
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
          Description description) {
        return representation.containsKey("proxy");
      }
    };
  }

}
