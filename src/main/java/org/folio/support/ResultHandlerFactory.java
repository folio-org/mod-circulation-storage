package org.folio.support;

import java.util.function.Consumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.NoStackTraceThrowable;

public class ResultHandlerFactory {
  public Handler<AsyncResult<String>> when(
    Consumer<String> onSuccess,
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

  private boolean hasNoCause(AsyncResult<String> result) {
    //When a failed vert.x future received a null cause,
    // it is replaced by an instance of NoStackTraceThrowable
    return result.cause() == null
      || result.cause() instanceof NoStackTraceThrowable;
  }
}
