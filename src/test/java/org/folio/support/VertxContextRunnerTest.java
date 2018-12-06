package org.folio.support;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Vertx;

public class VertxContextRunnerTest {
  private Vertx vertx;

  @Before
  public void beforeAll() {
    vertx = Vertx.vertx();
  }

  @After
  public void afterAll() {
    if(vertx != null) {
      vertx.close();
    }
  }

  @Test
  public void shouldExecuteErrorConsumerWhenExceptionIsThrown()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<Throwable> exceptionFuture = new CompletableFuture<>();

    final VertxContextRunner runner = new VertxContextRunner(
      vertx.getOrCreateContext(), exceptionFuture::complete);

    final RuntimeException expectedException
      = new RuntimeException("Something went wrong in runner");

    runner.runOnContext(() -> { throw expectedException; });

    Throwable receivedException = exceptionFuture.get(1, TimeUnit.SECONDS);

    assertThat("Failure consumer should be called with exception",
      receivedException, is(expectedException));

  }
}
