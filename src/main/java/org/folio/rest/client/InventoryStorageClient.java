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

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class InventoryStorageClient extends OkapiClient {
  private static final Logger log = LogManager.getLogger(InventoryStorageClient.class);

  public record CacheKey(String tenantId, String recordId) {}

  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String SERVICE_POINTS_COLLECTION_NAME = "servicepoints";

  private static final String LOCATION_URL = "/locations";
  private static final String LOCATION_COLLECTION_NAME = "locations";

  private static final Cache<CacheKey, Location> locationCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();

  private static final Cache<CacheKey, Servicepoint> servicePointCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();

  public InventoryStorageClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public static Cache<CacheKey, Location> locationCache() {
    return locationCache;
  }

  public static Cache<CacheKey, Servicepoint> servicePointCache() {
    return servicePointCache;
  }

  public static void invalidateLocation(String tenantId, String locationId) {
    log.info("invalidateLocation:: tenantId: {}, locationId: {}", tenantId, locationId);
    locationCache.invalidate(new CacheKey(tenantId, locationId));
  }

  public static void updateLocationCache(String tenantId, String locationId,
    Location location) {

    log.info("updateLocationCache:: tenantId: {}, locationId: {}", tenantId, locationId);
    locationCache.put(new CacheKey(tenantId, locationId), location);
  }

  public static void invalidateAllLocations() {
    log.info("invalidateAllLocations:: invalidating all location cache entries");
    locationCache.invalidateAll();
  }

  public static void invalidateServicePoint(String tenantId, String servicePointId) {
    log.info("invalidateServicePoint:: tenantId: {}, servicePointId: {}", tenantId, servicePointId);
    servicePointCache.invalidate(new CacheKey(tenantId, servicePointId));
  }

  public static void updateServicePointCache(String tenantId, String servicePointId,
    Servicepoint servicePoint) {

    log.info("updateServicePointCache:: tenantId: {}, servicePointId: {}", tenantId,
      servicePointId);
    servicePointCache.put(new CacheKey(tenantId, servicePointId), servicePoint);
  }

  public static void invalidateAllServicePoints() {
    log.info("invalidateAllServicePoints:: invalidating all service point cache entries");
    servicePointCache.invalidateAll();
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
    String collectionName, Class<T> type, Cache<CacheKey, T> cache,
    Function<T, String> idExtractor) {

    if (ids == null || ids.isEmpty()) {
      log.info("fetchWithCache:: ids are null or empty, returning empty collection");
      return succeededFuture(new ArrayList<>());
    }

    List<T> cachedResults = new ArrayList<>();
    List<String> missingIds = new ArrayList<>();

    for (String id : ids) {
      if (id == null) {
        log.warn("fetchWithCache:: null id encountered, skipping");
        continue;
      }
      T cached = cache.getIfPresent(new CacheKey(getTenant(), id));
      if (cached != null) {
        log.info("fetchWithCache:: Cache hit for tenantId: {}, id: {}", getTenant(), id);
        cachedResults.add(cached);
      } else {
        log.info("fetchWithCache:: Cache miss for tenantId: {}, id: {}", getTenant(), id);
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
          cache.put(new CacheKey(getTenant(), id), item);
          log.info("fetchWithCache:: Cached item with tenantId: {}, id: {}", getTenant(), id);
        }
      }))
      .map(fetched -> {
        List<T> merged = new ArrayList<>(cachedResults);
        merged.addAll(fetched);
        return merged;
      });
  }
}
