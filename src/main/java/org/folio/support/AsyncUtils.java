package org.folio.support;

import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import io.vertx.core.Future;

public class AsyncUtils {

  private AsyncUtils() {
    throw new UnsupportedOperationException("Utility class, do not instantiate");
  }

  public static <E, R> Future<Collection<R>> mapSequentially(
    Collection<E> collection, Function<E, Future<R>> mapper) {

    final List<R> results = new ArrayList<>();
    Future<R> future = succeededFuture();

    for (E element : collection) {
      future = future.compose(ignored -> mapper.apply(element)
        .onSuccess(results::add));
    }

    return future.map(results);
  }

}
