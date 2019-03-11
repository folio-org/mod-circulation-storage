package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

import org.folio.rest.resource.interfaces.PeriodicAPI;
import org.folio.support.ExpirationTool;

public class RequestExpiryImpl implements PeriodicAPI {

  @Override
  public long runEvery() {
    String intervalString = MODULE_SPECIFIC_ARGS.getOrDefault("request.expire.interval",
      "3600000");
    return Long.parseLong(intervalString);
  }

  @Override
  public void run(Vertx vertx, Context context) {
    context.runOnContext(v -> ExpirationTool.doRequestExpiration(vertx));
  }
}
