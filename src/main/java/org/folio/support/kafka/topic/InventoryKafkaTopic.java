package org.folio.support.kafka.topic;

import org.folio.kafka.services.KafkaTopic;

public enum InventoryKafkaTopic implements KafkaTopic {
  ITEM("item", 10);

  private final String topic;
  private final int partitions;

  InventoryKafkaTopic(String topic, int partitions) {
    this.topic = topic;
    this.partitions = partitions;
  }

  @Override
  public String moduleName() {
    return "inventory";
  }

  @Override
  public String topicName() {
    return topic;
  }

  @Override
  public int numPartitions() {
    return partitions;
  }
}

