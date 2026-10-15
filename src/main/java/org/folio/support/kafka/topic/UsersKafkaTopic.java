package org.folio.support.kafka.topic;

import org.folio.kafka.services.KafkaTopic;

/**
 * Kafka topics of mod-users that mod-circulation-storage consumes from.
 */
public enum UsersKafkaTopic implements KafkaTopic {
  USERS;

  @Override
  public String moduleName() {
    return "users";
  }

  @Override
  public String topicName() {
    return "users";
  }
}
