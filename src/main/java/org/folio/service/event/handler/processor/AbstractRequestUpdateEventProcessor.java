package org.folio.service.event.handler.processor;

import org.folio.persist.AbstractRepository;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.service.event.InventoryEventType;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

abstract class AbstractRequestUpdateEventProcessor extends UpdateEventProcessor<Request> {
  protected static final Cache<String, Location> locationCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .build();
  protected static final Cache<String, Servicepoint> servicePointCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .build();

  public AbstractRequestUpdateEventProcessor(InventoryEventType inventoryEventType,
    AbstractRepository<Request> repository) {

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
