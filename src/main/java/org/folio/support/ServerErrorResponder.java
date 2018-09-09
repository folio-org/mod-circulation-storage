package org.folio.support;

import static io.vertx.core.Future.succeededFuture;

import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class ServerErrorResponder {
  private final Function<String, Response> responseCreator;
  private final Handler<AsyncResult<Response>> handler;

  public ServerErrorResponder(
    Function<String, Response> responseCreator,
    Handler<AsyncResult<Response>> handler) {

    this.responseCreator = responseCreator;
    this.handler = handler;
  }

  public void withError(Throwable error) {
    withMessage(error.getMessage());
  }

  public void withMessage(String message) {
    handler.handle(succeededFuture(
      responseCreator.apply(message)));
  }
}
