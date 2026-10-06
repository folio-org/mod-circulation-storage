package org.folio.support.kafka.topic;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class AuditKafkaTopicTest {

  @Test
  void enumHasExactlyOneValue() {
    assertThat(AuditKafkaTopic.values().length, is(1));
    assertThat(AuditKafkaTopic.values()[0], is(AuditKafkaTopic.LOG_RECORD));
  }

  @Test
  void moduleName() {
    assertThat(AuditKafkaTopic.LOG_RECORD.moduleName(), is("audit"));
  }

  @Test
  void topicName() {
    assertThat(AuditKafkaTopic.LOG_RECORD.topicName(), is("LOG_RECORD"));
  }

  @Test
  void moduleTopicName() {
    assertThat(AuditKafkaTopic.LOG_RECORD.moduleTopicName(), is("audit.LOG_RECORD"));
  }

  @Test
  void numPartitions() {
    assertThat(AuditKafkaTopic.LOG_RECORD.numPartitions(), is(10));
  }

  @Test
  void fullTopicNameContainsTenantIdAndTopicNameAndModuleName() {
    String fullName = AuditKafkaTopic.LOG_RECORD.fullTopicName("test_tenant");
    assertThat(fullName, containsString("test_tenant"));
    assertThat(fullName, containsString("audit"));
    assertThat(fullName, containsString("LOG_RECORD"));
  }
}
