package org.folio.support;

import java.util.function.Consumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class ResultHandlerFactory {
  public Handler<AsyncResult<String>> when(
    Consumer<String> onSuccess,
    Consumer<Throwable> onFailure) {

    return reply -> {
      try {
        if(reply.succeeded()) {
          onSuccess.accept(reply.result());
        }
        else {
          onFailure.accept(reply.cause());
        }
      }
      catch(Exception e) {
        onFailure.accept(e);
      }
    };
  }
}
