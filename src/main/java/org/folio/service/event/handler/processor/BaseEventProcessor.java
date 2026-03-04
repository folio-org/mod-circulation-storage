package org.folio.service.event.handler.processor;

import org.folio.persist.AbstractRepository;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.service.event.InventoryEventType;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public abstract class BaseEventProcessor<T> extends UpdateEventProcessor<T> {
  protected static final Cache<String, Location> locationCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .build();
  protected static final Cache<String, Servicepoint> servicePointCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .build();

  public BaseEventProcessor(InventoryEventType inventoryEventType, AbstractRepository<T> repository) {
    super(inventoryEventType, repository);
  }

  public void invalidateLocationCache(String locationId) {
    locationCache.invalidate(locationId);
  }

  public void invalidateServicePointCache(String servicePointId) {
    servicePointCache.invalidate(servicePointId);
  }

  public void invalidateAllLocationCache() {
    locationCache.invalidateAll();
  }

  public void invalidateAllServicePointCache() {
    servicePointCache.invalidateAll();
  }
}
