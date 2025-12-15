package org.folio.support;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ServerErrorResponderTest {
  private static final Logger log = LogManager.getLogger();

  @Test
  public void shouldRespondWithMessage()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<AsyncResult<Response>> responseFuture
      = new CompletableFuture<>();

    final ServerErrorResponder responder = new ServerErrorResponder(
      s -> Response.serverError().entity(s).build(),
      responseFuture::complete, log);

    responder.withMessage("Something went wrong");

    final AsyncResult<Response> result = responseFuture.get(1, TimeUnit.SECONDS);

    assertThat("Should succeed", result.succeeded(), is(true));
    assertThat(result.result().getStatus(), is(500));
    assertThat(result.result().getEntity(), is("Something went wrong"));
  }

  @Test
  public void shouldRespondWithMessageFromException()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<AsyncResult<Response>> responseFuture
      = new CompletableFuture<>();

    final ServerErrorResponder responder = new ServerErrorResponder(
      s -> Response.serverError().entity(s).build(),
      responseFuture::complete, log);

    responder.withError(new RuntimeException("An exceptional occurrence"));

    final AsyncResult<Response> result = responseFuture.get(1, TimeUnit.SECONDS);

    assertThat("Should succeed", result.succeeded(), is(true));
    assertThat(result.result().getStatus(), is(500));
    assertThat(result.result().getEntity(), is("An exceptional occurrence"));
  }

  @Test
  public void shouldRespondWhenErrorIsNull()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<AsyncResult<Response>> responseFuture
      = new CompletableFuture<>();

    final ServerErrorResponder responder = new ServerErrorResponder(
      s -> Response.serverError().entity(s).build(),
      responseFuture::complete, log);

    responder.withError(null);

    final AsyncResult<Response> result = responseFuture.get(1, TimeUnit.SECONDS);

    assertThat("Should succeed", result.succeeded(), is(true));
    assertThat(result.result().getStatus(), is(500));
    assertThat(result.result().getEntity(), is("Unknown error cause"));
  }
}
