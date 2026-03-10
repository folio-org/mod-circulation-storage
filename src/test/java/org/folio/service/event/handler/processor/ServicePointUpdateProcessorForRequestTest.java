package org.folio.service.event.handler.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
  }

  @Test
  void testChangeIsReturnedOnNameUpdate() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "sp1").put("name", "NewName"))
      .put("old", new JsonObject().put("id", "sp1").put("name", "OldName"));

    List<?> changes = processor.collectRelevantChanges(payload).result();

    assertFalse(changes.isEmpty(), "Should return a change when service point name changes");
  }

  @Test
  void testNoChangeWhenNameIsSame() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "sp2").put("name", "SameName"))
      .put("old", new JsonObject().put("id", "sp2").put("name", "SameName"));

    List<?> changes = processor.collectRelevantChanges(payload).result();

    assertTrue(changes.isEmpty(), "Should not return changes when service point name is the same");
  }

  @Test
  void testChangeWithDifferentCaseNames() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "sp3").put("name", "Service Point"))
      .put("old", new JsonObject().put("id", "sp3").put("name", "service point"));

    List<?> changes = processor.collectRelevantChanges(payload).result();

    assertFalse(changes.isEmpty(), "Should return a change when service point name case changes");
  }

  @Test
  void testProcessorHandlesNullGracefully() {
    JsonObject payload = new JsonObject()
      .put("new", new JsonObject().put("id", "sp4"))
      .put("old", new JsonObject().put("id", "sp4"));

    List<?> changes = processor.collectRelevantChanges(payload).result();

    assertTrue(changes.isEmpty(), "Should handle null names without throwing exception");
  }
}

