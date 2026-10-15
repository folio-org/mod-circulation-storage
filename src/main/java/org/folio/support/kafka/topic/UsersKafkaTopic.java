package org.folio.support.kafka.topic;

import org.folio.kafka.services.KafkaTopic;

/**
 * Kafka topics of mod-users that mod-circulation-storage consumes from.
 */
public enum UsersKafkaTopic implements KafkaTopic {
  USERS("users", 10);

  private final String topic;
  private final int partitions;

  UsersKafkaTopic(String topic, int partitions) {
    this.topic = topic;
    this.partitions = partitions;
  }

  @Override
  public String moduleName() {
    return "users";
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
