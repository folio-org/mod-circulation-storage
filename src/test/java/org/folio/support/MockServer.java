package org.folio.support;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Fail.fail;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.support.PubSubConfig.getPubSubPassword;
import static org.folio.support.PubSubConfig.getPubSubUser;

public class MockServer extends AbstractVerticle {

  private static final List<JsonObject> publishedEvents = new ArrayList<>();
  private static final List<JsonObject> createdEventTypes = new ArrayList<>();
  private static final List<JsonObject> registeredPublishers = new ArrayList<>();
  private static final List<JsonObject> registeredSubscribers = new ArrayList<>();
  private static final List<String> deletedEventTypes = new ArrayList<>();

  private static boolean failPubSubRegistration;
  private static boolean failPubSubUnregistering;
  private static boolean failPublishingWithBadRequestError;

  private static final Logger logger = LoggerFactory.getLogger(MockServer.class);

  private final int port;
  private final Vertx vertx;

  public MockServer(int port, Vertx vertx) {
    this.port = port;
    this.vertx = vertx;
  }

  public void start() throws InterruptedException, ExecutionException, TimeoutException {
    HttpServer server = vertx.createHttpServer();
    CompletableFuture<HttpServer> deploymentComplete = new CompletableFuture<>();
    server.requestHandler(register()).listen(port, result -> {
      if(result.succeeded()) {
        deploymentComplete.complete(result.result());
      }
      else {
        deploymentComplete.completeExceptionally(result.cause());
      }
    });
    deploymentComplete.get(30, TimeUnit.SECONDS);
  }

  public void close() {
    vertx.close(res -> {
      if (res.failed()) {
        logger.error("Failed to shut down mock server", res.cause());
        fail(res.cause().getMessage());
      } else {
        logger.info("Successfully shut down mock server");
      }
    });
  }

  public Router register() {

    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());

    router.post("/authn/login").handler(MockServer::login);

    router.post("/pubsub/publish")
      .handler(routingContext -> {
          publishedEvents.add(routingContext.getBodyAsJson());
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

  private static void login(RoutingContext routingContext) {
      JsonObject json = routingContext.getBodyAsJson();
      Buffer buffer = Buffer.buffer(json.encodePrettily(), "UTF-8");
      String tenant = routingContext.request().getHeader(OKAPI_TENANT_HEADER);
      String username = json.getString("username");
      String password = json.getString("password");

      if (getPubSubUser().equals(username) && getPubSubPassword().equals(password)) {
        routingContext.response()
          .setStatusCode(HTTP_CREATED.toInt())
          .putHeader("content-type", "application/json; charset=utf-8")
          .putHeader("content-length", Integer.toString(buffer.length()))
          .putHeader(OKAPI_TOKEN_HEADER, generateOkapiToken("user", tenant))
          .write(buffer)
          .end();
      } else {
        routingContext.response()
          .setStatusCode(HTTP_UNPROCESSABLE_ENTITY.toInt())
          .putHeader("content-type", "application/json; charset=utf-8")
          .write(buffer)
          .end();
      }
  }

  private static void postTenant(RoutingContext routingContext,
                                 List<JsonObject> requestBodyList) {

    if (failPubSubRegistration) {
      routingContext.response()
        .setStatusCode(HTTP_INTERNAL_SERVER_ERROR.toInt())
        .end();
    }
    else {
      if (requestBodyList != null) {
        requestBodyList.add(routingContext.getBodyAsJson());
      }
      String json = routingContext.getBodyAsJson().encodePrettily();
      Buffer buffer = Buffer.buffer(json, "UTF-8");
      routingContext.response()
        .setStatusCode(HTTP_CREATED.toInt())
        .putHeader("content-type", "application/json; charset=utf-8")
        .putHeader("content-length", Integer.toString(buffer.length()))
        .write(buffer)
        .end();
    }
  }

  private static void deleteTenant(RoutingContext routingContext) {
    if (failPubSubUnregistering) {
      routingContext.response()
        .setStatusCode(HTTP_INTERNAL_SERVER_ERROR.toInt())
        .end();
    }
    else {
      deletedEventTypes.add(Arrays.asList(routingContext.normalisedPath().split("/")).get(3));

      routingContext.response()
        .setStatusCode(HTTP_NO_CONTENT.toInt())
        .end();
    }
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

  private static String generateOkapiToken(String user, String tenant) {
    final String payload = new JsonObject()
      .put("user", user)
      .put("tenant", tenant)
      .put("sub", "admin")
      .toString();
    return String.format("1.%s.3", Base64.getEncoder()
      .encodeToString(payload.getBytes()));
  }
}
