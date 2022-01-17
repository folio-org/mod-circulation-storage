package org.folio.service.event;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;

import io.vertx.core.Future;

public class EntityChangedEventPublisher<K, E> {

//  public static final String NULL_ENTIR_ID = "00000000-0000-0000-0000-000000000000";

  private final Map<String, String> okapiHeaders;
  private final EntityChangedEventFactory<E> eventFactory;
  private final GenericDomainEventPublisher<K, EntityChangedData<E>> eventPublisher;


  public EntityChangedEventPublisher(Map<String, String> okapiHeaders,
      EntityChangedEventFactory<E> eventFactory,
      GenericDomainEventPublisher<K, EntityChangedData<E>> eventPublisher) {
    this.okapiHeaders = okapiHeaders;
    this.eventFactory = eventFactory;
    this.eventPublisher = eventPublisher;
  }

  /*public EntityChangedEventPublisher(Context vertxContext, Map<String, String> okapiHeaders,
      KafkaTopic kafkaTopic) {

    this(okapiHeaders, kafkaTopic, createProducerManager(vertxContext),
        new EntityChangedEventFactory<>(), FailureHandler.noOperation());
  }*/

  Future<Void> publishCreated(K key, E newEntity) {
    return eventPublisher.publish(key, eventFactory.created(newEntity, tenantId(okapiHeaders)), okapiHeaders);
  }

  Future<Void> publishUpdated(K key, E oldEntity, E newEntity) {
    return eventPublisher.publish(key, eventFactory.updated(oldEntity, newEntity, tenantId(okapiHeaders)), okapiHeaders);
  }

  Future<Void> publishRemoved(K key, E oldEntity) {
    return eventPublisher.publish(key, eventFactory.deleted(oldEntity, tenantId(okapiHeaders)), okapiHeaders);
  }

  Future<Void> publishAllRemoved(K key) {
    return eventPublisher.publish(key, eventFactory.allDeleted(tenantId(okapiHeaders)), okapiHeaders);
  }

}
