package org.folio.service.tlr;

import static io.vertx.core.Future.succeededFuture;

import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;

import io.vertx.core.Future;

public class TlrFeatureToggleService {
  public Future<Void> run(TlrFeatureToggleJob job) {
    return succeededFuture();
  }
}
