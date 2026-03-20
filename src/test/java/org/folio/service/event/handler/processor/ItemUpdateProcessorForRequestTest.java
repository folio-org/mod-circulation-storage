package org.folio.service.event.handler.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.service.event.handler.BaseInventoryEventHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.github.benmanes.caffeine.cache.Cache;

class ItemUpdateProcessorForRequestTest {

  private TestEventHandler testEventHandler;

  @BeforeEach
  void setUp() {
    testEventHandler = new TestEventHandler();
    testEventHandler.getLocationCache().invalidateAll();
    testEventHandler.getServicePointCache().invalidateAll();
  }

  @Test
  void testLocationCacheSharedAcrossHandlers() {
    String locationId = "locShared";
    Location location = Mockito.mock(Location.class);
    testEventHandler.getLocationCache().put(locationId, location);
    assertNotNull(testEventHandler.getLocationCache().getIfPresent(locationId));
  }

  @Test
  void testServicePointCacheSharedAcrossHandlers() {
    String servicePointId = "spShared";
    Servicepoint sp = Mockito.mock(Servicepoint.class);
    testEventHandler.getServicePointCache().put(servicePointId, sp);
    assertNotNull(testEventHandler.getServicePointCache().getIfPresent(servicePointId));
  }

  @Test
  void testLocationCachePutAndGet() {
    String locationId = "locTest";
    Location location = Mockito.mock(Location.class);
    testEventHandler.getLocationCache().put(locationId, location);
    assertEquals(location, testEventHandler.getLocationCache().getIfPresent(locationId));
  }

  @Test
  void testServicePointCachePutAndGet() {
    String servicePointId = "spTest";
    Servicepoint sp = Mockito.mock(Servicepoint.class);
    testEventHandler.getServicePointCache().put(servicePointId, sp);
    assertEquals(sp, testEventHandler.getServicePointCache().getIfPresent(servicePointId));
  }

  private static class TestEventHandler extends BaseInventoryEventHandler {
    @Override
    public Cache<String, Location> getLocationCache() {
      return super.getLocationCache();
    }

    @Override
    public Cache<String, Servicepoint> getServicePointCache() {
      return super.getServicePointCache();
    }
  }
}
