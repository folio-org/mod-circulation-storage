package org.folio.support;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.junit.Assert.fail;

public class MockServer extends AbstractVerticle {

  private static final List<JsonObject> publishedEvents = new ArrayList<>();
  private static final List<JsonObject> createdEventTypes = new ArrayList<>();
  private static final List<JsonObject> registeredPublishers = new ArrayList<>();
  private static final List<JsonObject> registeredSubscribers = new ArrayList<>();
  private static final List<String> deletedEventTypes = new ArrayList<>();

  private static final Logger logger = LogManager.getLogger();

  private final int port;
  private final Vertx vertx;

  public MockServer(int port, Vertx vertx) {
    this.port = port;
    this.vertx = vertx;
  }

  public void start() throws InterruptedException, ExecutionException, TimeoutException {
    HttpServer server = vertx.createHttpServer();
    CompletableFuture<HttpServer> deploymentComplete = new CompletableFuture<>();
    server.requestHandler(register()).listen(port)
      .onSuccess(deploymentComplete::complete)
      .onFailure(deploymentComplete::completeExceptionally);
    deploymentComplete.get(30, TimeUnit.SECONDS);
  }

  public void close() {
    vertx.close()
      .onSuccess(res -> logger.info("Successfully shut down mock server"))
      .onFailure(cause -> {
        logger.error("Failed to shut down mock server", cause);
        fail(cause.getMessage());
      });
  }

  public Router register() {

    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());

    router.post("/pubsub/publish")
      .handler(routingContext -> {
          publishedEvents.add(routingContext.body().asJsonObject());
          routingContext.response()
            .setStatusCode(HTTP_NO_CONTENT.toInt())
            .end();
      });

    router.post("/pubsub/event-types")
      .handler(ctx -> postTenant(ctx, createdEventTypes));

    router.post("/pubsub/event-types/declare/publisher")
      .handler(ctx -> postTenant(ctx, registeredPublishers));

    router.post("/pubsub/event-types/declare/subscriber")
      .handler(ctx -> postTenant(ctx, registeredSubscribers));

    router.delete("/pubsub/event-types/:eventTypeName/publishers")
      .handler(MockServer::deleteTenant);

    return router;
  }

  private static void postTenant(RoutingContext routingContext,
                                 List<JsonObject> requestBodyList) {

    if (requestBodyList != null) {
      requestBodyList.add(routingContext.body().asJsonObject());
    }
    String json = routingContext.body().asJsonObject().encodePrettily();
    Buffer buffer = Buffer.buffer(json, "UTF-8");
    routingContext.response()
      .setStatusCode(HTTP_CREATED.toInt())
      .putHeader("content-type", "application/json; charset=utf-8")
      .putHeader("content-length", Integer.toString(buffer.length()))
      .end(buffer);
  }

  private static void deleteTenant(RoutingContext routingContext) {
    deletedEventTypes.add(Arrays.asList(routingContext.normalizedPath().split("/")).get(3));

    routingContext.response()
      .setStatusCode(HTTP_NO_CONTENT.toInt())
      .end();
  }

  public static List<JsonObject> getPublishedEvents() {
    return publishedEvents;
  }

  public static List<JsonObject> getCreatedEventTypes() {
    return createdEventTypes;
  }

  public static List<JsonObject> getRegisteredPublishers() {
    return registeredPublishers;
  }

  public static List<JsonObject> getRegisteredSubscribers() {
    return registeredSubscribers;
  }

  public static List<String> getDeletedEventTypes() {
    return deletedEventTypes;
  }

  public static void clearPublishedEvents() {
    publishedEvents.clear();
  }
}
