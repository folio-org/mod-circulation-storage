package org.folio;

import org.folio.kafka.services.KafkaTopic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum FolioKafkaTopic implements KafkaTopic {
  REQUEST(Module.CIRCULATION, "request", 10),
  LOAN(Module.CIRCULATION, "loan", 10),
  CHECK_IN(Module.CIRCULATION, "check-in", 10),
  ITEM(Module.INVENTORY, "item", 10);

  private final Module module;
  private final String topic;
  private final int partitions;

  @Override
  public String moduleName() {
    return module.getName();
  }

  @Override
  public String topicName() {
    return topic;
  }

  @Override
  public int numPartitions() {
    return partitions;
  }

  @RequiredArgsConstructor
  @Getter
  private enum Module {
    CIRCULATION("circulation"),
    INVENTORY("inventory");

    private final String name;
  }
}

