package org.folio.support.kafka.topic;

import org.folio.kafka.services.KafkaTopic;

public enum CirculationStorageKafkaTopic implements KafkaTopic {
  REQUEST("request", 10),
  LOAN("loan", 10),
  CHECK_IN("check-in", 10),
  RULES("rules", 10);

  private final String topic;
  private final int partitions;

  CirculationStorageKafkaTopic(String topic, int partitions) {
    this.topic = topic;
    this.partitions = partitions;
  }

  @Override
  public String moduleName() {
    return "circulation";
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

