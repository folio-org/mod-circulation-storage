package org.folio.service.event.handler.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.folio.persist.RequestRepository;
import org.folio.rest.client.InventoryStorageClient;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ItemUpdateProcessorForRequestTest {
  private ItemUpdateProcessorForRequest processor;
  private RequestRepository requestRepository;
  private InventoryStorageClient inventoryStorageClient;

  @BeforeEach
  void setUp() {
    requestRepository = Mockito.mock(RequestRepository.class);
    inventoryStorageClient = Mockito.mock(InventoryStorageClient.class);
    processor = new ItemUpdateProcessorForRequest(requestRepository, inventoryStorageClient);
    ItemUpdateProcessorForRequest.locationCache.invalidateAll();
    ItemUpdateProcessorForRequest.servicePointCache.invalidateAll();
  }

  @Test
  void testLocationCacheSharedWithOtherProcessors() {
    String locationId = "locShared";
    Location location = Mockito.mock(Location.class);
    ItemUpdateProcessorForRequest.locationCache.put(locationId, location);
    assertNotNull(ItemLocationUpdateProcessorForRequest.locationCache.getIfPresent(locationId));
  }

  @Test
  void testServicePointCacheSharedWithOtherProcessors() {
    String servicePointId = "spShared";
    Servicepoint sp = Mockito.mock(Servicepoint.class);
    ItemUpdateProcessorForRequest.servicePointCache.put(servicePointId, sp);
    assertNotNull(ServicePointUpdateProcessorForRequest.servicePointCache.getIfPresent(servicePointId));
  }

  @Test
  void testLocationCachePutAndGet() {
    String locationId = "locTest";
    Location location = Mockito.mock(Location.class);
    ItemUpdateProcessorForRequest.locationCache.put(locationId, location);
    assertEquals(location, ItemUpdateProcessorForRequest.locationCache.getIfPresent(locationId));
  }

  @Test
  void testServicePointCachePutAndGet() {
    String servicePointId = "spTest";
    Servicepoint sp = Mockito.mock(Servicepoint.class);
    ItemUpdateProcessorForRequest.servicePointCache.put(servicePointId, sp);
    assertEquals(sp, ItemUpdateProcessorForRequest.servicePointCache.getIfPresent(servicePointId));
  }
}

