package org.folio.service.event;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.LogManager.getLogger;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.support.ResponseUtil.isCreateSuccessResponse;
import static org.folio.support.ResponseUtil.isDeleteSuccessResponse;
import static org.folio.support.ResponseUtil.isUpdateSuccessResponse;

import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.Future;
import org.apache.logging.log4j.Logger;

public class EntityChangedEventPublisher<K, E> {

  private static final Logger log = getLogger(EntityChangedEventPublisher.class);

  private final Map<String, String> okapiHeaders;
  private final Function<E, K> keyExtractor;
  private final K nullKey;
  private final EntityChangedEventFactory<E> eventFactory;
  private final DomainEventPublisher<K, EntityChangedData<E>> eventPublisher;


  EntityChangedEventPublisher(Map<String, String> okapiHeaders,
      Function<E, K> keyExtractor, K nullKey,
      EntityChangedEventFactory<E> eventFactory,
      DomainEventPublisher<K, EntityChangedData<E>> eventPublisher) {
    this.okapiHeaders = okapiHeaders;
    this.keyExtractor = keyExtractor;
    this.nullKey = nullKey;
    this.eventFactory = eventFactory;
    this.eventPublisher = eventPublisher;
  }

  @SuppressWarnings("unchecked")
  public Function<Response, Future<Response>> publishCreated() {
    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Record create failed, skipping event publishing");
        
        return succeededFuture(response);
      }

      E entity = (E) response.getEntity();
      return publishCreated(keyExtractor.apply(entity), entity)
        .map(response);
    };
  }

  public Function<Response, Future<Response>> publishUpdated(E oldEntity) {
    return response -> {
      if (!isUpdateSuccessResponse(response)) {
        log.warn("Record update failed, skipping event publishing");
        return succeededFuture(response);
      }

      return publishUpdated(singletonList(oldEntity)).map(response);
    };
  }

  public Function<Response, Future<Response>> publishRemoved(E oldEntity) {
    return response -> {
      if (!isDeleteSuccessResponse(response)) {
        log.warn("Record removal failed, no event will be sent");
        return succeededFuture(response);
      }

      return publishRemoved(keyExtractor.apply(oldEntity), oldEntity)
          .map(response);
    };
  }

  public Function<Response, Future<Response>> publishAllRemoved() {
    return response -> {
      if (!isDeleteSuccessResponse(response)) {
        log.warn("Records removal failed, no event will be sent");

        return succeededFuture(response);
      }

      return publishAllRemoved(nullKey)
          .map(response);
    };
  }

  private Future<Void> publishCreated(K key, E newEntity) {
    return eventPublisher.publish(key, eventFactory.created(newEntity, tenantId(okapiHeaders)), okapiHeaders);
  }

  private Future<Void> publishUpdated(K key, E oldEntity, E newEntity) {
    return eventPublisher.publish(key, eventFactory.updated(oldEntity, newEntity, tenantId(okapiHeaders)), okapiHeaders);
  }

  private Future<Void> publishRemoved(K key, E oldEntity) {
    return eventPublisher.publish(key, eventFactory.deleted(oldEntity, tenantId(okapiHeaders)), okapiHeaders);
  }

  private Future<Void> publishAllRemoved(K key) {
    return eventPublisher.publish(key, eventFactory.allDeleted(tenantId(okapiHeaders)), okapiHeaders);
  }

}
