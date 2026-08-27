package org.folio.support.kafka.topic;

import org.folio.kafka.services.KafkaTopic;

/**
 * Kafka topics related to the audit module that mod-circulation-storage produces to.
 * Topics are owned and created by mod-audit; this module only publishes messages to them.
 */
public enum AuditKafkaTopic implements KafkaTopic {

  /**
   * Circulation log-record events
   */
  LOG_RECORD("LOG_RECORD", 10);

  private final String topicName;
  private final int numPartitions;

  AuditKafkaTopic(String topicName, int numPartitions) {
    this.topicName = topicName;
    this.numPartitions = numPartitions;
  }

  @Override
  public String moduleName() {
    return "audit";
  }

  @Override
  public String topicName() {
    return topicName;
  }

  @Override
  public int numPartitions() {
    return numPartitions;
  }
}
