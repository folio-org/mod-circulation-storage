package org.folio.rest.support.matchers;

import static org.folio.rest.support.JsonArrayHelper.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import java.util.List;
import java.util.Objects;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.core.IsCollectionContaining;

import io.vertx.core.json.JsonObject;

public class ValidationErrorMatchers {
  static TypeSafeDiagnosingMatcher<JsonObject> hasErrorWith(Matcher<JsonObject> matcher) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Validation error which ").appendDescriptionOf(matcher);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation, Description description) {
        final List<JsonObject> errors = toList(representation, "errors");

        if(errors.isEmpty()) {
          description.appendText("errors array is empty");
          return false;
        }

        final Matcher<Iterable<? super JsonObject>> iterableMatcher
          = IsCollectionContaining.hasItem(matcher);


        iterableMatcher.describeMismatch(errors, description);

        return iterableMatcher.matches(errors);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasParameter(
    String key,
    String value) {

    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has parameter with key ").appendValue(key)
          .appendText(" and value ").appendValue(value);
      }

      @Override
      protected boolean matchesSafely(JsonObject error, Description description) {
        final List<JsonObject> parameters = toList(error, "parameters");

        final boolean hasParameter = hasParameter(parameters, key, value);

        if(!hasParameter) {
          if(!hasParameter(parameters, key)) {
            description.appendText("does not have parameter ")
              .appendValue(key);
          }
          else {
            description.appendText("parameter has value ")
              .appendValue(getParameter(parameters, key));
          }
        }

        return hasParameter;
      }
    };
  }

  private static String getParameter(List<JsonObject> parameters, String key) {
    return parameters.stream().filter(parameter ->
      Objects.equals(parameter.getString("key"), key))
      .findFirst()
      .map(parameter -> parameter.getString("value"))
      .orElse(null);
  }

  private static boolean hasParameter(List<JsonObject> parameters, String key) {
    return parameters.stream().anyMatch(parameter ->
      Objects.equals(parameter.getString("key"), key));
  }

  private static boolean hasParameter(List<JsonObject> parameters, String key, String value) {
    return parameters.stream().anyMatch(parameter ->
      Objects.equals(parameter.getString("key"), key)
        && Objects.equals(parameter.getString("value"), value));
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasMessage(String message) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has message ").appendValue(message);
      }

      @Override
      protected boolean matchesSafely(JsonObject error, Description description) {
        final Matcher<String> matcher = is(message);

        matcher.describeMismatch(error, description);

        return matcher.matches(error.getString("message"));
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasMessageContaining(String message) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has message containing ").appendValue(message);
      }

      @Override
      protected boolean matchesSafely(JsonObject error, Description description) {
        final Matcher<String> matcher = containsString(message);

        matcher.describeMismatch(error, description);

        return matcher.matches(error.getString("message"));
      }
    };
  }
}
