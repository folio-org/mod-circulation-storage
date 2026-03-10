package org.folio.service.event.handler;

import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Base class for inventory event handlers that initializes and provides shared caching
 * for locations and service points.
 */
public abstract class BaseInventoryEventHandler {
  protected static final Cache<String, Location> locationCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .build();

  protected static final Cache<String, Servicepoint> servicePointCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .build();

  protected void invalidateLocationCache(String locationId) {
    locationCache.invalidate(locationId);
  }

  protected void invalidateServicePointCache(String servicePointId) {
    servicePointCache.invalidate(servicePointId);
  }

  protected void invalidateAllLocationCache() {
    locationCache.invalidateAll();
  }

  protected void invalidateAllServicePointCache() {
    servicePointCache.invalidateAll();
  }

  protected Cache<String, Location> getLocationCache() {
    return locationCache;
  }

  protected Cache<String, Servicepoint> getServicePointCache() {
    return servicePointCache;
  }
}

