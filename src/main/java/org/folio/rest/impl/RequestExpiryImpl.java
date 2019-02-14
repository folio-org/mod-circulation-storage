package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.folio.rest.resource.interfaces.PeriodicAPI;
import org.folio.support.ExpirationTool;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

public class RequestExpiryImpl implements PeriodicAPI {

  @Override
  public long runEvery() {
    String intervalString = MODULE_SPECIFIC_ARGS.getOrDefault("request.expire.interval",
      "360000");
    return Long.parseLong(intervalString);
  }

  @Override
  public void run(Vertx vertx, Context context) {
    context.runOnContext(v -> ExpirationTool.doRequestExpiration(vertx, context));
  }
}
