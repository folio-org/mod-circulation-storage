package org.folio.service.event;

import static org.folio.service.event.InventoryEventType.PayloadType.DELETE;
import static org.folio.service.event.InventoryEventType.PayloadType.UPDATE;
import static org.folio.support.kafka.topic.InventoryKafkaTopic.ITEM;
import static org.folio.support.kafka.topic.InventoryKafkaTopic.LOCATION;
import static org.folio.support.kafka.topic.InventoryKafkaTopic.SERVICE_POINT;

import org.folio.kafka.services.KafkaTopic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InventoryEventType {
  INVENTORY_ITEM_UPDATED(ITEM, UPDATE),
  INVENTORY_SERVICE_POINT_UPDATED(SERVICE_POINT, UPDATE),
  INVENTORY_SERVICE_POINT_DELETED(SERVICE_POINT, DELETE),
  INVENTORY_LOCATION_UPDATED(LOCATION, UPDATE);

  private final KafkaTopic kafkaTopic;
  private final PayloadType payloadType;

  public enum PayloadType {
    UPDATE, DELETE, CREATE, DELETE_ALL
  }
}
