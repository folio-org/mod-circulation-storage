package org.folio.rest.client;

import java.util.Collection;
import java.util.Map;

import org.folio.rest.jaxrs.model.Servicepoint;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class InventoryStorageClient extends OkapiClient {
  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String SERVICE_POINTS_COLLECTION_NAME = "servicepoints";

  public InventoryStorageClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<Collection<Servicepoint>> getServicePoints(Collection<String> ids) {
    return get(SERVICE_POINTS_URL, ids, SERVICE_POINTS_COLLECTION_NAME, Servicepoint.class);
  }

}
