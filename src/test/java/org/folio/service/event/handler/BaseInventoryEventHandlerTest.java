package org.folio.service.event.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;

class BaseInventoryEventHandlerTest {

  private TestEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new TestEventHandler();
    // Clear caches before each test
    handler.invalidateAllLocationCache();
    handler.invalidateAllServicePointCache();
  }

  @Test
  void testCachesAreInitialized() {
    Cache<String, Location> locationCache = handler.getLocationCache();
    Cache<String, Servicepoint> servicePointCache = handler.getServicePointCache();

    assertNotNull(locationCache, "Location cache should be initialized");
    assertNotNull(servicePointCache, "Service point cache should be initialized");
  }

  @Test
  void testLocationCacheOperations() {
    Cache<String, Location> cache = handler.getLocationCache();

    String locationId = "test-location-id";
    Location location = new Location();
    location.setId(locationId);
    location.setName("Test Location");

    // Put in cache
    cache.put(locationId, location);

    // Verify it's cached
    Location cached = cache.getIfPresent(locationId);
    assertNotNull(cached);
    assertEquals(locationId, cached.getId());
    assertEquals("Test Location", cached.getName());

    // Invalidate
    handler.invalidateLocationCache(locationId);

    // Verify it's gone
    assertNull(cache.getIfPresent(locationId));
  }

  @Test
  void testServicePointCacheOperations() {
    Cache<String, Servicepoint> cache = handler.getServicePointCache();

    String servicePointId = "test-sp-id";
    Servicepoint sp = new Servicepoint();
    sp.setId(servicePointId);
    sp.setName("Test Service Point");

    // Put in cache
    cache.put(servicePointId, sp);

    // Verify it's cached
    Servicepoint cached = cache.getIfPresent(servicePointId);
    assertNotNull(cached);
    assertEquals(servicePointId, cached.getId());
    assertEquals("Test Service Point", cached.getName());

    // Invalidate
    handler.invalidateServicePointCache(servicePointId);

    // Verify it's gone
    assertNull(cache.getIfPresent(servicePointId));
  }

  @Test
  void testInvalidateAllLocationCache() {
    Cache<String, Location> cache = handler.getLocationCache();

    // Add multiple locations
    for (int i = 0; i < 5; i++) {
      Location location = new Location();
      location.setId("location-" + i);
      cache.put("location-" + i, location);
    }

    // Verify they're all cached
    assertEquals(5, cache.estimatedSize());

    // Invalidate all
    handler.invalidateAllLocationCache();

    // Verify all are gone
    assertEquals(0, cache.estimatedSize());
  }

  @Test
  void testInvalidateAllServicePointCache() {
    Cache<String, Servicepoint> cache = handler.getServicePointCache();

    // Add multiple service points
    for (int i = 0; i < 5; i++) {
      Servicepoint sp = new Servicepoint();
      sp.setId("sp-" + i);
      cache.put("sp-" + i, sp);
    }

    // Verify they're all cached
    assertEquals(5, cache.estimatedSize());

    // Invalidate all
    handler.invalidateAllServicePointCache();

    // Verify all are gone
    assertEquals(0, cache.estimatedSize());
  }

  @Test
  void testCachesAreSharedAcrossHandlers() {
    // Create two handler instances
    TestEventHandler handler1 = new TestEventHandler();
    TestEventHandler handler2 = new TestEventHandler();

    // Add location via handler1
    String locationId = "shared-location";
    Location location = new Location();
    location.setId(locationId);
    handler1.getLocationCache().put(locationId, location);

    // Verify it's accessible from handler2 (same static cache)
    Location cached = handler2.getLocationCache().getIfPresent(locationId);
    assertNotNull(cached);
    assertEquals(locationId, cached.getId());
  }

  @Test
  void testCacheMaxSize() {
    Cache<String, Location> cache = handler.getLocationCache();

    for (int i = 0; i < 1500; i++) {
      Location location = new Location();
      location.setId("location-" + i);
      cache.put("location-" + i, location);
    }

    cache.cleanUp();

    long size = cache.estimatedSize();
    assertTrue(size <= 1100, "Cache size should be close to 1000 after eviction, but was: " + size);
  }

  @Test
  void testInvalidateNonExistentLocation() {
    // Should not throw exception when invalidating non-existent entry
    assertDoesNotThrow(() -> handler.invalidateLocationCache("non-existent-id"));
  }

  @Test
  void testInvalidateNonExistentServicePoint() {
    // Should not throw exception when invalidating non-existent entry
    assertDoesNotThrow(() -> handler.invalidateServicePointCache("non-existent-id"));
  }

  // Test helper class
  private static class TestEventHandler extends BaseInventoryEventHandler {
    // Expose protected methods for testing
    @Override
    public Cache<String, Location> getLocationCache() {
      return super.getLocationCache();
    }

    @Override
    public Cache<String, Servicepoint> getServicePointCache() {
      return super.getServicePointCache();
    }

    @Override
    public void invalidateLocationCache(String locationId) {
      super.invalidateLocationCache(locationId);
    }

    @Override
    public void invalidateServicePointCache(String servicePointId) {
      super.invalidateServicePointCache(servicePointId);
    }

    @Override
    public void invalidateAllLocationCache() {
      super.invalidateAllLocationCache();
    }

    @Override
    public void invalidateAllServicePointCache() {
      super.invalidateAllServicePointCache();
    }
  }
}

