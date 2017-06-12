package org.folio.rest.support;

import io.vertx.core.json.JsonObject;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class periodJsonObjectMatcher {
  public static Matcher matchesPeriod(Integer duration, String intervalId) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a period of %s %s", duration, intervalId));
      }

      @Override
      protected boolean matchesSafely(JsonObject period) {
        return period.getString("intervalId").equals(intervalId)
          && period.getInteger("duration").equals(duration);
      }
    };
  }
}
