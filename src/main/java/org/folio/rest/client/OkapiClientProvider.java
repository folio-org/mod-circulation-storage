package org.folio.rest.client;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

import java.util.HashMap;
import java.util.Map;

public class OkapiClientProvider {
  private static final Map<Vertx, WebClient> clients = new HashMap<>();

  private OkapiClientProvider() {
  }

  public static WebClient getWebClient(Vertx vertx) {
    return clients.computeIfAbsent(vertx, WebClient::create);
  }

}
