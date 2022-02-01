package org.folio.support;

import static io.vertx.core.Future.succeededFuture;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;

import java.util.UUID;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.persist.AbstractRepository;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.Request;
import org.folio.service.event.EntityChangedEventPublisher;

@RunWith(VertxUnitRunner.class)
public class ServiceHelperTest {

  private static final String ENTITY_ID = UUID.randomUUID().toString();
  private static final Request OLD_ENTITY = new Request().withId(ENTITY_ID).withStatus(OPEN_NOT_YET_FILLED);
  private static final Request NEW_ENTITY = new Request().withId(ENTITY_ID).withStatus(OPEN_IN_TRANSIT);

  private AbstractRepository<Request> repository = mock(RequestRepository.class);
  private EntityChangedEventPublisher<String, Request> eventPublisher = mock(EntityChangedEventPublisher.class);
  private ServiceHelper serviceHelper = new ServiceHelper(repository, eventPublisher);

  @Test
  public void shouldPublishCreatedEvent(TestContext testContext) {
    when(repository.getById(eq(ENTITY_ID))).thenReturn(succeededFuture());
    when(repository.upsert(eq(ENTITY_ID), any())).thenReturn(succeededFuture(ENTITY_ID));
    when(eventPublisher.publishCreated()).thenReturn(res -> succeededFuture(res));

    serviceHelper.upsertAndPublishEvents(ENTITY_ID, NEW_ENTITY)
      .onComplete(testContext.asyncAssertSuccess(notUsed -> {
        verify(repository).upsert(eq(ENTITY_ID), eq(NEW_ENTITY));
        verify(eventPublisher).publishCreated();
      }));
  }

  @Test
  public void shouldPushUpdatedEvent(TestContext testContext) {
    when(repository.getById(eq(ENTITY_ID))).thenReturn(succeededFuture(OLD_ENTITY));
    when(repository.upsert(any(), any())).thenReturn(succeededFuture(ENTITY_ID));
    when(eventPublisher.publishUpdated(any())).thenReturn(res -> succeededFuture(res));

    serviceHelper.upsertAndPublishEvents(ENTITY_ID, NEW_ENTITY)
      .onComplete(testContext.asyncAssertSuccess(notUsed -> {
        verify(repository).upsert(eq(ENTITY_ID), eq(NEW_ENTITY));
        verify(eventPublisher).publishUpdated(eq(OLD_ENTITY));
      }));
  }

  @Test
  public void shouldReturnJsonString() {
    var json = serviceHelper.jsonStringOrEmpty(NEW_ENTITY);
    assertFalse(StringUtils.isEmpty(json));
  }

  @Test
  public void shouldReturnEmptyStringIfNullObject() {
    var json = serviceHelper.jsonStringOrEmpty(null);
    assertTrue(StringUtils.isEmpty(json));
  }

  @Test
  public void shouldReturnEmptyStringIfSerializationFails() {
    var json = serviceHelper.jsonStringOrEmpty(new Object());
    assertTrue(StringUtils.isEmpty(json));
  }

}
