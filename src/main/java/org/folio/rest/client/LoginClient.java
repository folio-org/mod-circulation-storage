package org.folio.rest.client;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;

import java.util.Map;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.support.PubSubConfig.getPubSubPassword;
import static org.folio.support.PubSubConfig.getPubSubUser;

public class LoginClient extends OkapiClient {

  public LoginClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<String> postLoginRequest() {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    okapiPostAbs("/authn/login").sendJson(
      new JsonObject()
      .put("username", getPubSubUser())
      .put("password", getPubSubPassword()),
      promise);

    return promise.future().compose(response -> response.statusCode() == HTTP_CREATED  ?
      succeededFuture(response.getHeader(OKAPI_HEADER_TOKEN)) :
      failedFuture("Failed to post patron notice. Returned status code: " + response.statusCode()));
  }
}
