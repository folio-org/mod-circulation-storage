package org.folio.service.event;

import static org.folio.support.kafka.topic.InventoryKafkaTopic.ITEM;
import static org.folio.service.event.InventoryEventType.PayloadType.UPDATE;

import org.folio.kafka.services.KafkaTopic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InventoryEventType {
  INVENTORY_ITEM_UPDATED(ITEM, UPDATE);

  private final KafkaTopic kafkaTopic;
  private final PayloadType payloadType;

  public enum PayloadType {
    UPDATE, DELETE, CREATE, DELETE_ALL
  }
}
