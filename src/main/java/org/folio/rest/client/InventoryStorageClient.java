package org.folio.rest.client;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;

import com.github.benmanes.caffeine.cache.Cache;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class InventoryStorageClient extends OkapiClient {
  private static final Logger log = LogManager.getLogger(InventoryStorageClient.class);

  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String SERVICE_POINTS_COLLECTION_NAME = "servicepoints";

  private static final String LOCATION_URL = "/locations";
  private static final String LOCATION_COLLECTION_NAME = "locations";

  private final Cache<String, Location> locationCache;
  private final Cache<String, Servicepoint> servicePointCache;

  public InventoryStorageClient(Vertx vertx, Map<String, String> okapiHeaders) {
    this(vertx, okapiHeaders, null, null);
  }

  public InventoryStorageClient(Vertx vertx, Map<String, String> okapiHeaders,
    Cache<String, Location> locationCache, Cache<String, Servicepoint> servicePointCache) {

    super(vertx, okapiHeaders);
    this.locationCache = locationCache;
    this.servicePointCache = servicePointCache;
  }

  public Future<Collection<Servicepoint>> getServicePoints(Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return succeededFuture(new ArrayList<>());
    }

    // Check cache for single non-null ID requests if cache is available
    if (servicePointCache != null && ids.size() == 1) {
      String id = ids.iterator().next();
      if (id != null) {
        Servicepoint cached = servicePointCache.getIfPresent(id);
        if (cached != null) {
          log.info("getServicePoints:: Cache hit for service point id: {}", id);
          return succeededFuture(singletonList(cached));
        }
        log.info("getServicePoints:: Cache miss for service point id: {}", id);
      }
    }

    return get(SERVICE_POINTS_URL, ids, SERVICE_POINTS_COLLECTION_NAME, Servicepoint.class)
      .onSuccess(servicePoints -> {
        if (servicePointCache != null) {
          servicePoints.forEach(sp -> {
            if (sp.getId() != null) {
              servicePointCache.put(sp.getId(), sp);
              log.debug("getServicePoints:: Cached service point id: {}", sp.getId());
            }
          });
        }
      });
  }

  public Future<Collection<Location>> getLocations(Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return succeededFuture(new ArrayList<>());
    }

    // Check cache for single non-null ID requests if cache is available
    if (locationCache != null && ids.size() == 1) {
      String id = ids.iterator().next();
      if (id != null) {
        Location cached = locationCache.getIfPresent(id);
        if (cached != null) {
          log.info("getLocations:: Cache hit for location id: {}", id);
          return succeededFuture(singletonList(cached));
        }
        log.info("getLocations:: Cache miss for location id: {}", id);
      }
    }

    return get(LOCATION_URL, ids, LOCATION_COLLECTION_NAME, Location.class)
      .onSuccess(locations -> {
        if (locationCache != null) {
          locations.forEach(loc -> {
            if (loc.getId() != null) {
              locationCache.put(loc.getId(), loc);
              log.debug("getLocations:: Cached location id: {}", loc.getId());
            }
          });
        }
      });
  }
}
