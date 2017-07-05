package org.folio.rest.support;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class ResponseHandler {
  private static final Logger log = LoggerFactory.getLogger(ResponseHandler.class);

  public static Handler<HttpClientResponse> empty(
    CompletableFuture<Response> completed) {

    return response -> {
      try {
        int statusCode = response.statusCode();

        completed.complete(new Response(statusCode));
      }
      catch(Exception e) {
        completed.completeExceptionally(e);
      }
    };
  }

  public static Handler<HttpClientResponse> json(
    CompletableFuture<JsonResponse> completed) {

    return response -> {
      response.bodyHandler(buffer -> {
        try {
          int statusCode = response.statusCode();
          String body = BufferHelper.stringFromBuffer(buffer);

          log.debug(String.format("Response: %s", body));

          completed.complete(new JsonResponse(statusCode, body));

        } catch(Exception e) {
          completed.completeExceptionally(e);
        }
      });
    };
  }

  public static Handler<HttpClientResponse> jsonErrors(
    CompletableFuture<JsonErrorResponse> completed) {

    return response -> {
      response.bodyHandler(buffer -> {
        try {
          int statusCode = response.statusCode();
          String body = BufferHelper.stringFromBuffer(buffer);

          completed.complete(new JsonErrorResponse(statusCode, body));

        } catch (Exception e) {
          completed.completeExceptionally(e);
        }
      });
    };
  }

  public static Handler<HttpClientResponse> text(
    CompletableFuture<TextResponse> completed) {

    return response -> {
        int statusCode = response.statusCode();

        response.bodyHandler(buffer -> {
          try {
            String body = BufferHelper.stringFromBuffer(buffer);

            completed.complete(new TextResponse(statusCode, body));

          } catch (Exception e) {
            completed.completeExceptionally(e);
          }
        });
    };
  }
}
