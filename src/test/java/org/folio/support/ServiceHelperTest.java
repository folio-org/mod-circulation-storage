package org.folio.support;

import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;

import java.util.UUID;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.persist.AbstractRepository;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.Request;
import org.folio.service.event.EntityChangedEventPublisher;

@RunWith(VertxUnitRunner.class)
public class ServiceHelperTest {

  private AbstractRepository<Request> repository = mock(RequestRepository.class);
  private EntityChangedEventPublisher<String, Request> eventPublisher = mock(EntityChangedEventPublisher.class);
  private ServiceHelper serviceHelper = new ServiceHelper(repository, eventPublisher);

  @Test
  public void shouldPublishCreatedEvent(TestContext testContext) {
    var id = UUID.randomUUID().toString();
    var newEntity = new Request().withId(id).withStatus(OPEN_IN_TRANSIT);

    when(repository.getById(eq(id))).thenReturn(succeededFuture());
    when(repository.upsert(eq(id), any())).thenReturn(succeededFuture(id));
    when(eventPublisher.publishCreated()).thenReturn(res -> Future.succeededFuture(res));

    serviceHelper.upsertAndPublishEvents(id, newEntity)
      .onComplete(testContext.asyncAssertSuccess(notUsed -> {
        verify(repository).upsert(eq(id), eq(newEntity));
        verify(eventPublisher).publishCreated();
      }));
  }

  @Test
  public void shouldPushUpdatedEvent(TestContext testContext) {
    var id = UUID.randomUUID().toString();
    var oldEntity = new Request().withId(id).withStatus(OPEN_NOT_YET_FILLED);
    var newEntity = new Request().withId(id).withStatus(OPEN_IN_TRANSIT);

    when(repository.getById(eq(id))).thenReturn(succeededFuture(oldEntity));
    when(repository.upsert(any(), any())).thenReturn(succeededFuture(id));
    when(eventPublisher.publishUpdated(any())).thenReturn(res -> Future.succeededFuture(res));

    serviceHelper.upsertAndPublishEvents(id, newEntity)
      .onComplete(testContext.asyncAssertSuccess(notUsed -> {
        verify(repository).upsert(eq(id), eq(newEntity));
        verify(eventPublisher).publishUpdated(eq(oldEntity));
      }));
  }

}
