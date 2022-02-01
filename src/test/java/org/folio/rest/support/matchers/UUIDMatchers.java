package org.folio.rest.support.matchers;

import static org.hamcrest.CoreMatchers.is;

import java.util.UUID;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.TypeSafeMatcher;

public class UUIDMatchers {
  public static TypeSafeDiagnosingMatcher<String> isUUID(UUID expected) {
    return new TypeSafeDiagnosingMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("a string UUID of ").appendValue(expected);
      }

      @Override
      protected boolean matchesSafely(String actual, Description description) {
        Matcher<String> uuidMatcher = is(expected.toString());

        uuidMatcher.describeMismatch(actual, description);

        return uuidMatcher.matches(actual);
      }
    };
  }

  public static Matcher<String> hasUUIDFormat() {

    return new TypeSafeMatcher<String>() {

      @Override
      protected boolean matchesSafely(String uuidAsString) {
        try {
          UUID.fromString(uuidAsString);
        } catch (IllegalArgumentException ex) {
          return false;
        }

        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("should be an UUID string");
      }

    };
  }

}
