package org.folio.rest.client;

import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    return fetchWithCache(ids, SERVICE_POINTS_URL, SERVICE_POINTS_COLLECTION_NAME,
      Servicepoint.class, servicePointCache, Servicepoint::getId);
  }

  public Future<Collection<Location>> getLocations(Collection<String> ids) {
    return fetchWithCache(ids, LOCATION_URL, LOCATION_COLLECTION_NAME,
      Location.class, locationCache, Location::getId);
  }

  private <T> Future<Collection<T>> fetchWithCache(Collection<String> ids, String url,
    String collectionName, Class<T> type, Cache<String, T> cache,
    Function<T, String> idExtractor) {

    if (ids == null || ids.isEmpty()) {
      log.info("fetchWithCache:: ids are null or empty, returning empty collection");
      return succeededFuture(new ArrayList<>());
    }

    if (cache == null) {
      log.info("fetchWithCache:: cache is not available, fetching {} ids from {}",
        ids.size(), url);
      return get(url, ids, collectionName, type);
    }

    List<T> cachedResults = new ArrayList<>();
    List<String> missingIds = new ArrayList<>();

    for (String id : ids) {
      T cached = cache.getIfPresent(id);
      if (cached != null) {
        log.debug("fetchWithCache:: Cache hit for id: {}", id);
        cachedResults.add(cached);
      } else {
        log.debug("fetchWithCache:: Cache miss for id: {}", id);
        missingIds.add(id);
      }
    }

    if (missingIds.isEmpty()) {
      return succeededFuture(cachedResults);
    }

    return get(url, missingIds, collectionName, type)
      .onSuccess(fetched -> fetched.forEach(item -> {
        String id = idExtractor.apply(item);
        if (id != null) {
          cache.put(id, item);
          log.debug("fetchWithCache:: Cached item with id: {}", id);
        }
      }))
      .map(fetched -> {
        List<T> merged = new ArrayList<>(cachedResults);
        merged.addAll(fetched);
        return merged;
      });
  }
}
