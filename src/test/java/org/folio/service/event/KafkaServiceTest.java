package org.folio.service.event;

import static io.vertx.core.Future.succeededFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.support.kafka.topic.CirculationStorageKafkaTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

class KafkaServiceTest {

  private static final String TENANT_ID = "test_tenant";

  private KafkaAdminClientService adminClientService;
  private KafkaService kafkaService;

  @BeforeEach
  void setUp() {
    adminClientService = mock(KafkaAdminClientService.class);
    when(adminClientService.createKafkaTopics(any(), anyString())).thenReturn(succeededFuture());
    when(adminClientService.deleteKafkaTopics(any(), anyString())).thenReturn(succeededFuture());

    kafkaService = new KafkaService(adminClientService);
  }

  @Test
  void createCirculationStorageTopicsDelegatesToAdminService() {
    Future<Void> result = kafkaService.createCirculationStorageTopics(TENANT_ID);

    assertThat(result.succeeded(), is(true));
    verify(adminClientService, times(1))
      .createKafkaTopics(CirculationStorageKafkaTopic.values(), TENANT_ID);
  }

  @Test
  void deleteCirculationStorageTopicsDelegatesToAdminService() {
    Future<Void> result = kafkaService.deleteCirculationStorageTopics(TENANT_ID);

    assertThat(result.succeeded(), is(true));
    verify(adminClientService, times(1))
      .deleteKafkaTopics(CirculationStorageKafkaTopic.values(), TENANT_ID);
  }

  @Test
  void createTopicsDelegatesToAdminService() {
    var topics = new CirculationStorageKafkaTopic[]{ CirculationStorageKafkaTopic.LOG_RECORD };

    Future<Void> result = kafkaService.createTopics(topics, TENANT_ID);

    assertThat(result.succeeded(), is(true));
    verify(adminClientService, times(1)).createKafkaTopics(topics, TENANT_ID);
  }

  @Test
  void createPublisherReturnsKafkaEventPublisher() {
    Context context = mock(Context.class);
    var mock = mock(Vertx.class);
    when(context.owner()).thenReturn(mock);

    var publisher = kafkaService.createPublisher(CirculationStorageKafkaTopic.LOG_RECORD, context, TENANT_ID);

    assertThat(publisher, notNullValue());
    assertThat(publisher, instanceOf(KafkaEventPublisher.class));
  }

}
