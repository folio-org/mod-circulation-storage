package org.folio.service.event.handler.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.folio.rest.client.InventoryStorageClient;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ItemUpdateProcessorForRequestTest {

  private static final String TENANT = "test_tenant";

  @BeforeEach
  void setUp() {
    InventoryStorageClient.invalidateAllLocations();
    InventoryStorageClient.invalidateAllServicePoints();
  }

  @Test
  void testLocationCacheSharedAcrossHandlers() {
    InventoryStorageClient.CacheKey key = new InventoryStorageClient.CacheKey(TENANT, "locShared");
    Location location = Mockito.mock(Location.class);

    InventoryStorageClient.locationCache().put(key, location);

    assertNotNull(InventoryStorageClient.locationCache().getIfPresent(key));
  }

  @Test
  void testServicePointCacheSharedAcrossHandlers() {
    InventoryStorageClient.CacheKey key = new InventoryStorageClient.CacheKey(TENANT, "spShared");
    Servicepoint sp = Mockito.mock(Servicepoint.class);

    InventoryStorageClient.servicePointCache().put(key, sp);

    assertNotNull(InventoryStorageClient.servicePointCache().getIfPresent(key));
  }

  @Test
  void testLocationCachePutAndGet() {
    InventoryStorageClient.CacheKey key = new InventoryStorageClient.CacheKey(TENANT, "locTest");
    Location location = Mockito.mock(Location.class);

    InventoryStorageClient.locationCache().put(key, location);

    assertEquals(location, InventoryStorageClient.locationCache().getIfPresent(key));
  }

  @Test
  void testServicePointCachePutAndGet() {
    InventoryStorageClient.CacheKey key = new InventoryStorageClient.CacheKey(TENANT, "spTest");
    Servicepoint sp = Mockito.mock(Servicepoint.class);

    InventoryStorageClient.servicePointCache().put(key, sp);

    assertEquals(sp, InventoryStorageClient.servicePointCache().getIfPresent(key));
  }
}
