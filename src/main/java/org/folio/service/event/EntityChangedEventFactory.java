package org.folio.service.event;

import static org.folio.service.event.DomainEventType.ALL_DELETED;
import static org.folio.service.event.DomainEventType.CREATED;
import static org.folio.service.event.DomainEventType.DELETED;
import static org.folio.service.event.DomainEventType.UPDATED;

import java.util.UUID;

public class EntityChangedEventFactory<T> {

  public DomainEvent<EntityChangedData<T>> updated(T oldEntity, T newEntity, String tenant) {
    return create(UPDATED, oldEntity, newEntity, tenant);
  }

  public DomainEvent<EntityChangedData<T>> created(T newEntity, String tenant) {
    return create(CREATED, null, newEntity, tenant);
  }

  public DomainEvent<EntityChangedData<T>> deleted(T oldEntity, String tenant) {
    return create(DELETED, oldEntity, null, tenant);
  }

  public DomainEvent<EntityChangedData<T>> allDeleted(String tenant) {
    return create(ALL_DELETED, null, null, tenant);
  }

  private DomainEvent<EntityChangedData<T>> create(DomainEventType type, T oldEntity,
      T newEntity, String tenant) {
    return DomainEvent.<EntityChangedData<T>>builder()
        .id(UUID.randomUUID())
        .type(type)
        .tenant(tenant)
        .timestamp(currentTimestamp())
        .data(new EntityChangedData<>(oldEntity, newEntity))
        .build();
  }

  private static long currentTimestamp() {
    return System.currentTimeMillis();
  }

}