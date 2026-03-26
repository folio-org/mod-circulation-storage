package org.folio.rest.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;

class InventoryStorageClientCacheTest {

  private static final String TENANT = "test_tenant";

  @BeforeEach
  void setUp() {
    InventoryStorageClient.invalidateAllLocations();
    InventoryStorageClient.invalidateAllServicePoints();
  }

  @Test
  void testLocationCacheOperations() {
    String locationId = "test-location-id";
    InventoryStorageClient.CacheKey key = new InventoryStorageClient.CacheKey(TENANT, locationId);
    Location location = new Location().withId(locationId).withName("Test Location");

    InventoryStorageClient.locationCache().put(key, location);

    Location cached = InventoryStorageClient.locationCache().getIfPresent(key);
    assertNotNull(cached);
    assertEquals(locationId, cached.getId());
    assertEquals("Test Location", cached.getName());

    InventoryStorageClient.invalidateLocation(TENANT, locationId);

    assertNull(InventoryStorageClient.locationCache().getIfPresent(key));
  }

  @Test
  void testServicePointCacheOperations() {
    String servicePointId = "test-sp-id";
    InventoryStorageClient.CacheKey key = new InventoryStorageClient.CacheKey(TENANT, servicePointId);
    Servicepoint sp = new Servicepoint().withId(servicePointId).withName("Test Service Point");

    InventoryStorageClient.servicePointCache().put(key, sp);

    Servicepoint cached = InventoryStorageClient.servicePointCache().getIfPresent(key);
    assertNotNull(cached);
    assertEquals(servicePointId, cached.getId());
    assertEquals("Test Service Point", cached.getName());

    InventoryStorageClient.invalidateServicePoint(TENANT, servicePointId);

    assertNull(InventoryStorageClient.servicePointCache().getIfPresent(key));
  }

  @Test
  void testInvalidateAllLocations() {
    Cache<InventoryStorageClient.CacheKey, Location> cache = InventoryStorageClient.locationCache();
    for (int i = 0; i < 5; i++) {
      cache.put(new InventoryStorageClient.CacheKey(TENANT, "location-" + i), new Location().withId("location-" + i));
    }
    assertEquals(5, cache.estimatedSize());

    InventoryStorageClient.invalidateAllLocations();

    assertEquals(0, cache.estimatedSize());
  }

  @Test
  void testInvalidateAllServicePoints() {
    Cache<InventoryStorageClient.CacheKey, Servicepoint> cache = InventoryStorageClient.servicePointCache();
    for (int i = 0; i < 5; i++) {
      cache.put(new InventoryStorageClient.CacheKey(TENANT, "sp-" + i), new Servicepoint().withId("sp-" + i));
    }
    assertEquals(5, cache.estimatedSize());

    InventoryStorageClient.invalidateAllServicePoints();

    assertEquals(0, cache.estimatedSize());
  }

  @Test
  void testCacheIsolatedByTenant() {
    String locationId = "shared-location";
    InventoryStorageClient.CacheKey tenantAKey = new InventoryStorageClient.CacheKey("tenant_a", locationId);
    InventoryStorageClient.CacheKey tenantBKey = new InventoryStorageClient.CacheKey("tenant_b", locationId);

    InventoryStorageClient.locationCache().put(tenantAKey, new Location().withId(locationId));

    assertNotNull(InventoryStorageClient.locationCache().getIfPresent(tenantAKey));
    assertNull(InventoryStorageClient.locationCache().getIfPresent(tenantBKey),
      "Entry for tenant_a should not be visible under tenant_b key");
  }

  @Test
  void testCachesAreSharedAcrossInstances() {
    String locationId = "shared-location";
    InventoryStorageClient.CacheKey key = new InventoryStorageClient.CacheKey(TENANT, locationId);
    InventoryStorageClient.locationCache().put(key, new Location().withId(locationId));

    assertNotNull(InventoryStorageClient.locationCache().getIfPresent(key));
  }

  @Test
  void testCacheMaxSize() {
    Cache<InventoryStorageClient.CacheKey, Location> cache = InventoryStorageClient.locationCache();
    cache.invalidateAll();
    cache.cleanUp();

    for (int i = 0; i < 1500; i++) {
      cache.put(new InventoryStorageClient.CacheKey(TENANT, "location-" + i), new Location().withId("location-" + i));
    }
    cache.cleanUp();

    long size = cache.estimatedSize();
    assertTrue(size <= 1500, "Cache size should be less than input size after eviction, but was: " + size);
    assertTrue(size >= 1000, "Cache size should maintain at least max size entries, but was: " + size);
  }

  @Test
  void testInvalidateNonExistentLocation() {
    assertDoesNotThrow(() -> InventoryStorageClient.invalidateLocation(TENANT, "non-existent-id"));
  }

  @Test
  void testInvalidateNonExistentServicePoint() {
    assertDoesNotThrow(() -> InventoryStorageClient.invalidateServicePoint(TENANT, "non-existent-id"));
  }
}
