package org.folio.rest.support.matchers;

import static org.hamcrest.core.AllOf.allOf;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import io.vertx.core.json.JsonObject;

public final class CollectionMatchers {

  private CollectionMatchers() {}

  /**
   * This matcher is primarily used for comparing auto generated schema classes.
   * These classes do not have equals and hasCode methods + usually we don't want
   * to compare auto managed fields like metadata.
   *
   * This method uses json representation of the class to compare them.
   *
   * @param prototype - Model class entity all properties of which we should compare
   *                  with an iterable item.
   * @param <T> - Model class type.
   * @return Matcher.
   */
  public static <T> Matcher<Iterable<T>> hasItemLike(T prototype) {
    return new TypeSafeMatcher<Iterable<T>>() {
      private final JsonObject prototypeJson = toJson(prototype);
      @Override
      protected boolean matchesSafely(Iterable<T> iterable) {
        for (T currentElement : iterable) {
          if (looksLikePrototype(currentElement)) {
            return true;
          }
        }

        return false;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("An iterable contains item with all the properties as: ")
          .appendValue(prototypeJson);
      }

      private JsonObject toJson(T pojo) {
        return JsonObject.mapFrom(pojo);
      }

      private boolean looksLikePrototype(T currentElement) {
        final JsonObject currentElementJson = toJson(currentElement);

        for (Map.Entry<String, Object> entry : prototypeJson) {
          final Object prototypeValue = entry.getValue();
          final Object currentElementValue = currentElementJson.getValue(entry.getKey());

          if (!Objects.equals(prototypeValue, currentElementValue)) {
            return false;
          }
        }

        return true;
      }
    };
  }

  @SafeVarargs
  public static <T> Matcher<Iterable<T>> hasItemsLike(T first, T ... rest) {
    return allOf(Stream.concat(Stream.of(first), Stream.of(rest))
      .map(CollectionMatchers::hasItemLike)
      .collect(Collectors.toList()));
  }
}
