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

class ItemLocationUpdateProcessorForRequestTest {
  private ItemLocationUpdateProcessorForRequest processor;
  private RequestRepository requestRepository;

  @BeforeEach
  void setUp() {
    requestRepository = Mockito.mock(RequestRepository.class);
    processor = new ItemLocationUpdateProcessorForRequest(requestRepository);
    ItemLocationUpdateProcessorForRequest.locationCache.invalidateAll();
  }

  @Test
  void testCacheInvalidationOnUpdate() {
    String locationId = "loc1";
    ItemLocationUpdateProcessorForRequest.locationCache.put(locationId, Mockito.mock(org.folio.rest.jaxrs.model.Location.class));
    assertNotNull(ItemLocationUpdateProcessorForRequest.locationCache.getIfPresent(locationId));

    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", locationId).put("name", "NewName"))
      .put("old", new JsonObject().put("id", locationId).put("name", "OldName"));
    processor.collectRelevantChanges(payload);

    assertNull(ItemLocationUpdateProcessorForRequest.locationCache.getIfPresent(locationId));
  }

  @Test
  void testNoInvalidationIfNoId() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("name", "NewName"))
      .put("old", new JsonObject().put("name", "OldName"));
    processor.collectRelevantChanges(payload);
    assertTrue(ItemLocationUpdateProcessorForRequest.locationCache.asMap().isEmpty());
  }

  @Test
  void testChangeIsReturnedOnNameUpdate() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "loc2").put("name", "NewName"))
      .put("old", new JsonObject().put("id", "loc2").put("name", "OldName"));
    List<?> changes = processor.collectRelevantChanges(payload).result();
    assertFalse(changes.isEmpty());
  }
}

