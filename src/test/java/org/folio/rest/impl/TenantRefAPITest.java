package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.service.PubSubRegistrationService;
import org.folio.service.event.KafkaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TenantRefAPITest {

  private static final String TENANT_ID = "test-tenant";
  private static final Map<String, String> HEADERS = Map.of(XOkapiHeaders.TENANT, TENANT_ID);

  @AfterEach
  void tearDown() {
    TenantRefAPI.disableNativeKafkaIntegration();
  }

  @Test
  void enableAndDisableNativeKafkaIntegration() {
    // Just verify no exceptions are thrown when toggling the flag
    TenantRefAPI.enableNativeKafkaIntegration();
    TenantRefAPI.disableNativeKafkaIntegration();
  }

  @Test
  void whenFlagDisabledItCallsPubSubRegistration(VertxTestContext testContext, Vertx vertx) {
    TenantRefAPI.disableNativeKafkaIntegration();

    try (MockedConstruction<KafkaService> kafkaMock =
           Mockito.mockConstruction(KafkaService.class);
         MockedStatic<PubSubRegistrationService> pubSubMock =
           Mockito.mockStatic(PubSubRegistrationService.class)) {

      pubSubMock.when(() -> PubSubRegistrationService.registerModule(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(true));

      Context vertxContext = mock(Context.class);
      when(vertxContext.owner()).thenReturn(vertx);

      Future<?> result = new TenantRefAPI()
        .createKafkaTopicsOrRegisterPubSub(TENANT_ID, HEADERS, vertxContext);

      result.onComplete(ar -> testContext.verify(() -> {
        assertThat(ar.succeeded(), is(true));
        pubSubMock.verify(() -> PubSubRegistrationService.registerModule(HEADERS, vertx), times(1));
        assertThat("KafkaService should NOT be instantiated", kafkaMock.constructed().isEmpty(), is(true));
        testContext.completeNow();
      }));
    }
  }

  @Test
  void whenFlagEnabledItCreatesKafkaTopics(VertxTestContext testContext, Vertx vertx) {
    TenantRefAPI.enableNativeKafkaIntegration();

    try (MockedConstruction<KafkaService> kafkaMock =
           Mockito.mockConstruction(KafkaService.class,
             (mock, ctx) -> when(mock.createCirculationStorageTopics(anyString()))
               .thenReturn(succeededFuture()));
         MockedStatic<PubSubRegistrationService> pubSubMock =
           Mockito.mockStatic(PubSubRegistrationService.class)) {

      Context vertxContext = mock(Context.class);
      when(vertxContext.owner()).thenReturn(vertx);

      Future<?> result = new TenantRefAPI()
        .createKafkaTopicsOrRegisterPubSub(TENANT_ID, HEADERS, vertxContext);

      result.onComplete(ar -> testContext.verify(() -> {
        assertThat(ar.succeeded(), is(true));
        pubSubMock.verify(() -> PubSubRegistrationService.registerModule(any(), any()), never());
        assertThat(kafkaMock.constructed().size(), is(1));
        verify(kafkaMock.constructed().get(0), times(1)).createCirculationStorageTopics(TENANT_ID);
        testContext.completeNow();
      }));
    }
  }

}
