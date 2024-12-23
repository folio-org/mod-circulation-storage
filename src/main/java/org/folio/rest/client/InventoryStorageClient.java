package org.folio.rest.client;

import java.util.Collection;
import java.util.Map;

import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class InventoryStorageClient extends OkapiClient {
  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String SERVICE_POINTS_COLLECTION_NAME = "servicepoints";

  private static final String LOCATION_URL = "/locations";
  private static final String LOCATION_COLLECTION_NAME = "locations";

  public InventoryStorageClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<Collection<Servicepoint>> getServicePoints(Collection<String> ids) {
    return get(SERVICE_POINTS_URL, ids, SERVICE_POINTS_COLLECTION_NAME, Servicepoint.class);
  }

  public Future<Collection<Location>> getLocations(Collection<String> ids) {
    return get(LOCATION_URL, ids, LOCATION_COLLECTION_NAME, Location.class);
  }

}
