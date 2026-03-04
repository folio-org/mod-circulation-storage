package org.folio.service.event.handler.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.folio.persist.RequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.vertx.core.json.JsonObject;

class ServicePointUpdateProcessorForRequestTest {
  private ServicePointUpdateProcessorForRequest processor;
  private RequestRepository requestRepository;

  @BeforeEach
  void setUp() {
    requestRepository = Mockito.mock(RequestRepository.class);
    processor = new ServicePointUpdateProcessorForRequest(requestRepository);
    // Clear shared cache before each test
    ServicePointUpdateProcessorForRequest.servicePointCache.invalidateAll();
  }

  @Test
  void testCacheInvalidationOnUpdate() {
    // Put a dummy value in cache
    String servicePointId = "sp1";
    ServicePointUpdateProcessorForRequest.servicePointCache.put(servicePointId, Mockito.mock(org.folio.rest.jaxrs.model.Servicepoint.class));
    assertNotNull(ServicePointUpdateProcessorForRequest.servicePointCache.getIfPresent(servicePointId));

    // Simulate update event
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", servicePointId).put("name", "NewName"))
      .put("old", new JsonObject().put("id", servicePointId).put("name", "OldName"));
    processor.collectRelevantChanges(payload);

    // Cache should be invalidated
    assertNull(ServicePointUpdateProcessorForRequest.servicePointCache.getIfPresent(servicePointId));
  }

  @Test
  void testNoInvalidationIfNoId() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("name", "NewName"))
      .put("old", new JsonObject().put("name", "OldName"));
    processor.collectRelevantChanges(payload);
    // Nothing to invalidate, cache remains empty
    assertTrue(ServicePointUpdateProcessorForRequest.servicePointCache.asMap().isEmpty());
  }

  @Test
  void testChangeIsReturnedOnNameUpdate() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "sp2").put("name", "NewName"))
      .put("old", new JsonObject().put("id", "sp2").put("name", "OldName"));
    List<?> changes = processor.collectRelevantChanges(payload).result();
    assertFalse(changes.isEmpty());
  }
}

