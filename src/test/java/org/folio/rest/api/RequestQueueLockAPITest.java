package org.folio.rest.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpStatus;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

class RequestQueueLockAPITest extends ApiTests {
  private static final String TABLE_NAME = "request_queue_lock";

  private final AssertingRecordClient client = new AssertingRecordClient(
    super.client, StorageTestSuite.TENANT_ID,
    InterfaceUrls::requestQueueLockStorageUrl, "requestQueueLock");

  @BeforeEach
  void cleanUp() throws Exception {
    StorageTestSuite.cleanUpTable(TABLE_NAME);
  }

  @Test
  @SneakyThrows
  void onlyOneOwnerCanAcquireAQueueLock() {
    String queueId = UUID.randomUUID().toString();

    JsonResponse acquired = client.attemptCreate(lock("INSTANCE", queueId, 10_000));
    JsonResponse contended = client.attemptCreate(lock("INSTANCE", queueId, 10_000));

    assertThat(acquired.getStatusCode(), is(HttpStatus.SC_CREATED));
    assertThat(acquired.getJson().getString("id"), notNullValue());
    assertThat(acquired.getJson().getString("createdAt"), notNullValue());
    assertThat(acquired.getJson().getString("expiresAt"), notNullValue());
    assertThat(contended.getStatusCode(), is(HttpStatus.SC_CONFLICT));
  }

  @Test
  @SneakyThrows
  void concurrentAcquireAttemptsProduceExactlyOneOwner() {
    String queueId = UUID.randomUUID().toString();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch completed = new CountDownLatch(2);
    List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

    Runnable acquire = () -> {
      ready.countDown();
      try {
        start.await();
        statuses.add(client.attemptCreate(lock("INSTANCE", queueId, 10_000))
          .getStatusCode());
      } catch (Throwable throwable) {
        failures.add(throwable);
      } finally {
        completed.countDown();
      }
    };

    new Thread(acquire).start();
    new Thread(acquire).start();

    boolean bothAttemptsReady = ready.await(5, TimeUnit.SECONDS);
    start.countDown();
    Assertions.assertTrue(bothAttemptsReady);
    Assertions.assertTrue(completed.await(10, TimeUnit.SECONDS));
    Assertions.assertTrue(failures.isEmpty(), failures.toString());
    assertThat(statuses, containsInAnyOrder(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT));
  }

  @Test
  @SneakyThrows
  void queueTypeIsPartOfTheLockKey() {
    String queueId = UUID.randomUUID().toString();

    assertThat(client.attemptCreate(lock("INSTANCE", queueId, 10_000)).getStatusCode(),
      is(HttpStatus.SC_CREATED));
    assertThat(client.attemptCreate(lock("ITEM", queueId, 10_000)).getStatusCode(),
      is(HttpStatus.SC_CREATED));
  }

  @Test
  @SneakyThrows
  void releaseIsIdempotentAndAllowsReacquisition() {
    String queueId = UUID.randomUUID().toString();
    JsonResponse acquired = client.attemptCreate(lock("ITEM", queueId, 10_000));
    String lockId = acquired.getJson().getString("id");

    assertThat(client.attemptDeleteById(lockId).getStatusCode(),
      is(HttpStatus.SC_NO_CONTENT));
    assertThat(client.attemptDeleteById(lockId).getStatusCode(),
      is(HttpStatus.SC_NO_CONTENT));
    assertThat(client.attemptCreate(lock("ITEM", queueId, 10_000)).getStatusCode(),
      is(HttpStatus.SC_CREATED));
  }

  @Test
  @SneakyThrows
  void expiredOwnerCannotDeleteReplacementLock() {
    String queueId = UUID.randomUUID().toString();
    JsonResponse first = client.attemptCreate(lock("INSTANCE", queueId, 1));
    String firstLockId = first.getJson().getString("id");

    Thread.sleep(25);
    JsonResponse replacement = client.attemptCreate(lock("INSTANCE", queueId, 10_000));
    assertThat(replacement.getStatusCode(), is(HttpStatus.SC_CREATED));

    assertThat(client.attemptDeleteById(firstLockId).getStatusCode(),
      is(HttpStatus.SC_NO_CONTENT));
    assertThat(client.attemptCreate(lock("INSTANCE", queueId, 10_000)).getStatusCode(),
      is(HttpStatus.SC_CONFLICT));
  }

  private JsonObject lock(String queueType, String queueId, int ttlMs) {
    return new JsonObject()
      .put("queueType", queueType)
      .put("queueId", queueId)
      .put("ttlMs", ttlMs);
  }
}
