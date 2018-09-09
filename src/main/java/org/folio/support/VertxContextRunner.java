package org.folio.support;

import java.util.function.Consumer;

import io.vertx.core.Context;

public class VertxContextRunner {
  private final Context vertxContext;
  private final Consumer<Throwable> onError;

  public VertxContextRunner(
    Context vertxContext,
    Consumer<Throwable> onError) {

    this.vertxContext = vertxContext;
    this.onError = onError;
  }

  public void runOnContext(Runnable runnable) {
    this.vertxContext.runOnContext(v -> {
      try {
        runnable.run();
      }
      catch(Exception e) {
        this.onError.accept(e);
      }
    });
  }
}
