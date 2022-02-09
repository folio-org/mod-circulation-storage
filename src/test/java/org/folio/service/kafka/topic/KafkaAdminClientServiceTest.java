package org.folio.service.kafka.topic;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.support.Environment.environmentName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;

@RunWith(VertxUnitRunner.class)
public class KafkaAdminClientServiceTest {

  private final Set<String> allExpectedTopics = Set.of(
      "folio.foo-tenant.circulation.request",
      "folio.foo-tenant.circulation.loan",
      "folio.foo-tenant.circulation.check-in"
  );

  private KafkaAdminClient mockClient;

  @Before
  public void setUp() {
    mockClient = mock(KafkaAdminClient.class);
  }

  @Test
  public void shouldCreateTopicIfAlreadyExist(TestContext testContext) {
    when(mockClient.createTopics(anyList()))
        .thenReturn(failedFuture(new TopicExistsException("x")))
        .thenReturn(failedFuture(new TopicExistsException("y")))
        .thenReturn(failedFuture(new TopicExistsException("z")))
        .thenReturn(succeededFuture());
    when(mockClient.listTopics()).thenReturn(succeededFuture(Set.of("old")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
        .onComplete(testContext.asyncAssertSuccess(notUsed -> {
          verify(mockClient, times(4)).listTopics();
          verify(mockClient, times(4)).createTopics(anyList());
          verify(mockClient, times(1)).close();
        }));
  }

  @Test
  public void shouldFailIfExistExceptionIsPermanent(TestContext testContext) {
    when(mockClient.createTopics(anyList())).thenReturn(failedFuture(new TopicExistsException("x")));
    when(mockClient.listTopics()).thenReturn(succeededFuture(Set.of("old")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
        .onComplete(testContext.asyncAssertFailure(e -> {
          assertThat(e, instanceOf(TopicExistsException.class));
          verify(mockClient, times(1)).close();
        }));
  }

  @Test
  public void shouldNotCreateTopicOnOther(TestContext testContext) {
    when(mockClient.createTopics(anyList())).thenReturn(failedFuture(new RuntimeException("err msg")));
    when(mockClient.listTopics()).thenReturn(succeededFuture(Set.of("old")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
        .onComplete(testContext.asyncAssertFailure(cause -> {
              testContext.assertEquals("err msg", cause.getMessage());
              verify(mockClient, times(1)).close();
            }
        ));
  }

  @Test
  public void shouldCreateTopicIfNotExist(TestContext testContext) {
    when(mockClient.createTopics(anyList())).thenReturn(succeededFuture());
    when(mockClient.listTopics()).thenReturn(succeededFuture(Set.of("old")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
        .onComplete(testContext.asyncAssertSuccess(notUsed -> {

          @SuppressWarnings("unchecked")
          final ArgumentCaptor<List<NewTopic>> createTopicsCaptor = forClass(List.class);

          verify(mockClient, times(1)).createTopics(createTopicsCaptor.capture());
          verify(mockClient, times(1)).close();

          // Only these items are expected, so implicitly checks size of list
          assertThat(getTopicNames(createTopicsCaptor), containsInAnyOrder(allExpectedTopics.toArray()));
        }));
  }

  @Test
  public void shouldDeleteTopics(TestContext testContext) {
    when(mockClient.deleteTopics(anyList())).thenReturn(succeededFuture());
    when(mockClient.close()).thenReturn(succeededFuture());

    deleteKafkaTopicsAsync(mockClient)
        .onComplete(testContext.asyncAssertSuccess(notUsed -> {

          @SuppressWarnings("unchecked")
          final ArgumentCaptor<List<String>> deleteTopicsCaptor = forClass(List.class);

          verify(mockClient, times(1)).deleteTopics(deleteTopicsCaptor.capture());
          verify(mockClient, times(1)).close();

          assertThat(deleteTopicsCaptor.getAllValues().get(0), containsInAnyOrder(allExpectedTopics.toArray()));
        }));
  }

  private List<String> getTopicNames(ArgumentCaptor<List<NewTopic>> createTopicsCaptor) {
    return createTopicsCaptor.getAllValues().get(0).stream()
        .map(NewTopic::getName)
        .collect(Collectors.toList());
  }

  private Future<Void> createKafkaTopicsAsync(KafkaAdminClient client) {
    return new KafkaAdminClientService(() -> client)
        .createKafkaTopics("foo-tenant", environmentName());
  }

  private Future<Void> deleteKafkaTopicsAsync(KafkaAdminClient client) {
    return new KafkaAdminClientService(() -> client)
        .deleteKafkaTopics("foo-tenant", environmentName());
  }

}
