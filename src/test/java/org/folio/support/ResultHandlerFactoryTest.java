package org.folio.support;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.support.ResultHandlerFactory.when;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class ResultHandlerFactoryTest {
  @Test
  public void shouldExecuteConsumerOnSuccess() {
    AtomicBoolean onSuccessCalled = new AtomicBoolean(false);
    AtomicBoolean onFailureCalled = new AtomicBoolean(false);

    final Handler<AsyncResult<String>> resultHandler = when(
        s -> onSuccessCalled.set(true),
        e -> onFailureCalled.set(true));

    resultHandler.handle(succeededFuture("foo"));

    assertThat("Success consumer should be called",
      onSuccessCalled.get(), is(true));

    assertThat("Failure consumer should not be called",
      onFailureCalled.get(), is(false));
  }

  @Test
  public void shouldExecuteConsumerOnFailure() {

    AtomicBoolean onSuccessCalled = new AtomicBoolean(false);
    AtomicBoolean onFailureCalled = new AtomicBoolean(false);

    final Handler<AsyncResult<String>> resultHandler = when(
        s -> onSuccessCalled.set(true),
        e -> onFailureCalled.set(true));

    resultHandler.handle(failedFuture(new RuntimeException("unexpected failure")));

    assertThat("Failure consumer should be called",
      onFailureCalled.get(), is(true));

    assertThat("Success consumer should not be called",
      onSuccessCalled.get(), is(false));
  }

  @Test
  public void shouldExecuteFailureConsumerOnExceptionInSuccessConsumer() {
    final RuntimeException expectedException
      = new RuntimeException("Something went wrong in success handler");

    AtomicReference<Throwable> receivedException = new AtomicReference<>();

    final Handler<AsyncResult<String>> resultHandler = when(
        s -> { throw expectedException; },
        receivedException::set);

    resultHandler.handle(succeededFuture("foo"));

    assertThat("Failure consumer should be called with exception",
      receivedException.get(), is(expectedException));
  }

  @Test
  public void shouldExecuteFailureConsumerWhenResultIsNull() {
    AtomicReference<Throwable> receivedException = new AtomicReference<>();

    final Handler<AsyncResult<String>> resultHandler = when(
        s -> { throw new RuntimeException("Should not be called"); },
        receivedException::set);

    resultHandler.handle(null);

    assertThat("Failure consumer should be called with runtime exception",
      receivedException.get(), instanceOf(RuntimeException.class));

    assertThat("Exception should describe unknown error cause",
      receivedException.get().getMessage(), is("Result should not be null"));
  }

  @Test
  public void shouldExecuteFailureConsumerWhenFailureCauseIsNull() {
    AtomicReference<Throwable> receivedException = new AtomicReference<>();

    final Handler<AsyncResult<String>> resultHandler = when(
        s -> { throw new RuntimeException("Should not be called"); },
        receivedException::set);

    resultHandler.handle(failedFuture((Throwable) null));

    assertThat("Failure consumer should be called with runtime exception",
      receivedException.get(), instanceOf(RuntimeException.class));

    assertThat("Exception should describe unknown error cause",
      receivedException.get().getMessage(), is("Unknown error cause"));
  }
}
