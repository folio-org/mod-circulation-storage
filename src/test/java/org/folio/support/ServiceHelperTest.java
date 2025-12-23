package org.folio.support;

import static io.vertx.core.Future.succeededFuture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;

import java.util.UUID;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.folio.persist.AbstractRepository;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.Request;
import org.folio.service.event.EntityChangedEventPublisher;

@ExtendWith(VertxExtension.class)
class ServiceHelperTest {

  private static final String ENTITY_ID = UUID.randomUUID().toString();
  private static final Request OLD_ENTITY = new Request().withId(ENTITY_ID).withStatus(OPEN_NOT_YET_FILLED);
  private static final Request NEW_ENTITY = new Request().withId(ENTITY_ID).withStatus(OPEN_IN_TRANSIT);

  private final AbstractRepository<Request> repository = mock(RequestRepository.class);
  @SuppressWarnings("unchecked")
  private final EntityChangedEventPublisher<String, Request> eventPublisher = (EntityChangedEventPublisher<String, Request>) mock(EntityChangedEventPublisher.class);
  private final ServiceHelper<Request> serviceHelper = new ServiceHelper<>(repository, eventPublisher);

  @Test
  void shouldPublishCreatedEvent(VertxTestContext testContext) {
    when(repository.getById(eq(ENTITY_ID))).thenReturn(succeededFuture());
    when(repository.upsert(eq(ENTITY_ID), any())).thenReturn(succeededFuture(ENTITY_ID));
    when(eventPublisher.publishCreated()).thenReturn(Future::succeededFuture);

    serviceHelper.upsertAndPublishEvents(ENTITY_ID, NEW_ENTITY)
      .onComplete(ar -> {
        testContext.verify(() -> {
          assertTrue(ar.succeeded());
          verify(repository).upsert(eq(ENTITY_ID), eq(NEW_ENTITY));
          verify(eventPublisher).publishCreated();
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldPushUpdatedEvent(VertxTestContext testContext) {
    when(repository.getById(eq(ENTITY_ID))).thenReturn(succeededFuture(OLD_ENTITY));
    when(repository.upsert(any(), any())).thenReturn(succeededFuture(ENTITY_ID));
    when(eventPublisher.publishUpdated(any())).thenReturn(Future::succeededFuture);

    serviceHelper.upsertAndPublishEvents(ENTITY_ID, NEW_ENTITY)
      .onComplete(ar -> {
        testContext.verify(() -> {
          assertTrue(ar.succeeded());
          verify(repository).upsert(eq(ENTITY_ID), eq(NEW_ENTITY));
          verify(eventPublisher).publishUpdated(eq(OLD_ENTITY));
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldReturnJsonString() {
    var json = serviceHelper.jsonStringOrEmpty(NEW_ENTITY);
    assertFalse(StringUtils.isEmpty(json));
  }

  @Test
  void shouldReturnEmptyStringIfNullObject() {
    var json = serviceHelper.jsonStringOrEmpty(null);
    assertTrue(StringUtils.isEmpty(json));
  }

  @Test
  void shouldReturnEmptyStringIfSerializationFails() {
    var json = serviceHelper.jsonStringOrEmpty(new Object());
    assertTrue(StringUtils.isEmpty(json));
  }

}
