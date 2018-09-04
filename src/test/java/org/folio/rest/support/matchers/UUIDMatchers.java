package org.folio.rest.support.matchers;

import static org.hamcrest.CoreMatchers.is;

import java.util.UUID;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

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
}
