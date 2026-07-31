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
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

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
  }

  @Test
  void createCirculationStorageTopicsCallsAdminService() {
    try (MockedConstruction<KafkaAdminClientService> mocked =
        Mockito.mockConstruction(KafkaAdminClientService.class,
          (mock, ctx) -> when(mock.createKafkaTopics(any(), anyString())).thenReturn(succeededFuture()))) {

      kafkaService = new KafkaService(mock(Vertx.class));
      Future<Void> result = kafkaService.createCirculationStorageTopics(TENANT_ID);

      assertThat(result.succeeded(), is(true));
      verify(mocked.constructed().get(0), times(1))
        .createKafkaTopics(CirculationStorageKafkaTopic.values(), TENANT_ID);
    }
  }

  @Test
  void deleteCirculationStorageTopicsCallsAdminService() {
    try (MockedConstruction<KafkaAdminClientService> mocked =
        Mockito.mockConstruction(KafkaAdminClientService.class,
          (mock, ctx) -> when(mock.deleteKafkaTopics(any(), anyString())).thenReturn(succeededFuture()))) {

      kafkaService = new KafkaService(mock(Vertx.class));
      Future<Void> result = kafkaService.deleteCirculationStorageTopics(TENANT_ID);

      assertThat(result.succeeded(), is(true));
      verify(mocked.constructed().get(0), times(1))
        .deleteKafkaTopics(CirculationStorageKafkaTopic.values(), TENANT_ID);
    }
  }

  @Test
  void createPublisherReturnsPublisher() {
    try (MockedConstruction<KafkaAdminClientService> ignored =
        Mockito.mockConstruction(KafkaAdminClientService.class)) {

      kafkaService = new KafkaService(mock(Vertx.class));
      Context context = mock(Context.class);
      when(context.owner()).thenReturn(mock(Vertx.class));

      var publisher = kafkaService.createPublisher(CirculationStorageKafkaTopic.LOG_RECORD, context, TENANT_ID);

      assertThat(publisher, is(notNullValue()));
      assertThat(publisher, instanceOf(KafkaEventPublisher.class));
    }
  }

}
