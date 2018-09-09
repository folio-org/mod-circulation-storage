package org.folio.support;

import java.util.function.Consumer;

import io.vertx.core.Context;

public class VertxContextRunner {
  public void runOnContext(
    Context vertxContext,
    Consumer<Throwable> onError,
    Runnable runnable) {

    vertxContext.runOnContext(v -> {
      try {
        runnable.run();
      }
      catch(Exception e) {
        onError.accept(e);
      }
    });
  }
}
