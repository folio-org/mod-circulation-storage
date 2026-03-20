package org.folio.service.event.handler.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
  }

  @Test
  void testChangeIsReturnedOnNameUpdate() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "loc1").put("name", "NewName"))
      .put("old", new JsonObject().put("id", "loc1").put("name", "OldName"));

    List<?> changes = processor.collectRelevantChanges(payload).result();

    assertFalse(changes.isEmpty(), "Should return a change when location name changes");
  }

  @Test
  void testNoChangeWhenNameIsSame() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "loc2").put("name", "SameName"))
      .put("old", new JsonObject().put("id", "loc2").put("name", "SameName"));

    List<?> changes = processor.collectRelevantChanges(payload).result();

    assertTrue(changes.isEmpty(), "Should not return changes when location name is the same");
  }

  @Test
  void testChangeWithDifferentCaseNames() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "loc3").put("name", "Location Name"))
      .put("old", new JsonObject().put("id", "loc3").put("name", "location name"));

    List<?> changes = processor.collectRelevantChanges(payload).result();

    assertFalse(changes.isEmpty(), "Should return a change when location name case changes");
  }

  @Test
  void testProcessorHandlesNullGracefully() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "loc4"))
      .put("old", new JsonObject().put("id", "loc4"));

    List<?> changes = processor.collectRelevantChanges(payload).result();

    assertTrue(changes.isEmpty(), "Should handle null names without throwing exception");
  }
}

