package org.folio.support;

import java.util.function.Consumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public final class ResultHandlerFactory {
  private ResultHandlerFactory() {}

  public static <T> Handler<AsyncResult<T>> when(Consumer<T> onSuccess,
    Consumer<Throwable> onFailure) {

    return result -> {
      try {
        if(result == null) {
          onFailure.accept(new RuntimeException("Result should not be null"));
        }
        else if(result.succeeded()) {
          onSuccess.accept(result.result());
        }
        else {
          if(hasNoCause(result)) {
            onFailure.accept(new RuntimeException("Unknown error cause"));
          }
          else {
            onFailure.accept(result.cause());
          }
        }
      }
      catch(Exception e) {
        onFailure.accept(e);
      }
    };
  }

  private static <T> boolean hasNoCause(AsyncResult<T> result) {
    return new ServerError().isUnknown(result.cause());
  }
}
