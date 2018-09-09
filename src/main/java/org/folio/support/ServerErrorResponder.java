package org.folio.support;

import static io.vertx.core.Future.succeededFuture;

import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;

public class ServerErrorResponder {
  private final Function<String, Response> responseCreator;
  private final Handler<AsyncResult<Response>> handler;
  private final Logger log;

  public ServerErrorResponder(
    Function<String, Response> responseCreator,
    Handler<AsyncResult<Response>> handler,
    Logger log) {

    this.responseCreator = responseCreator;
    this.handler = handler;
    this.log = log;
  }

  public void withError(Throwable error) {
    if (error == null) {
      final String unknownErrorMessage = "Unknown error cause";

      log.error(unknownErrorMessage);
      withMessage(unknownErrorMessage);
    } else {
      log.error(error, error.getMessage());
      withMessage(error.getMessage());
    }
  }

  void withMessage(String message) {
    handler.handle(succeededFuture(
      responseCreator.apply(message)));
  }
}
