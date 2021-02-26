package org.folio.rest.support;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import io.vertx.ext.web.client.HttpResponse;

public class ResponseHandler {
  private static final Logger log = LogManager.getLogger();

  public static Handler<AsyncResult<HttpResponse<Buffer>>> empty(
    CompletableFuture<Response> completed) {

    return asyncResponse -> {
      if (asyncResponse.failed()) {
        completed.completeExceptionally(asyncResponse.cause());
        return;
      }

      HttpResponse<Buffer> response = asyncResponse.result();
      try {
        int statusCode = response.statusCode();
        completed.complete(new Response(statusCode));
      }
      catch(Exception e) {
        completed.completeExceptionally(e);
      }
    };
  }

  public static Handler<AsyncResult<HttpResponse<Buffer>>> json(
    CompletableFuture<JsonResponse> completed) {

    return asyncResponse -> {
      if (asyncResponse.failed()) {
        completed.completeExceptionally(asyncResponse.cause());
        return;
      }

      try {
        HttpResponse<Buffer> response = asyncResponse.result();
        int statusCode = response.statusCode();
        String body = response.bodyAsString();

        log.info(String.format("Response: '%s'", body));

        completed.complete(new JsonResponse(statusCode, body));

      } catch(Exception e) {
        completed.completeExceptionally(e);
      }
    };
  }

  public static Handler<AsyncResult<HttpResponse<Buffer>>> text(
    CompletableFuture<TextResponse> completed) {

    return asyncResponse -> {
      if (asyncResponse.failed()) {
        completed.completeExceptionally(asyncResponse.cause());
        return;
      }

      try {
        HttpResponse<Buffer> response = asyncResponse.result();
        int statusCode = response.statusCode();
        String body = response.bodyAsString();

        log.info(String.format("Response: '%s'", body));

        completed.complete(new TextResponse(statusCode, body));

      } catch (Exception e) {
        completed.completeExceptionally(e);
      }
    };
  }
}
