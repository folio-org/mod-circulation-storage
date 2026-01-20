package org.folio.rest.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.CheckoutLockRequest;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.MultipleRecords;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class CheckOutLockAPITest extends ApiTests {

  private static final String CHECK_OUT_LOCK_TABLE = "check_out_lock";

  private final AssertingRecordClient checkOutLockClient =
    new AssertingRecordClient(client, StorageTestSuite.TENANT_ID,
      InterfaceUrls::checkOutStorageUrl, "checkoutLock");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void beforeEach() throws Exception {
    StorageTestSuite.cleanUpTable(CHECK_OUT_LOCK_TABLE);
  }

  @SneakyThrows
  @Test
  void canCreateCheckOutLock() {

    String userId1 = UUID.randomUUID().toString();
    JsonObject checkOutLock1 = toJsonObject(createCheckoutLockRequest(userId1, 1000));
    checkOutLockClient.create(checkOutLock1);

    JsonObject checkOutLock2 = toJsonObject(createCheckoutLockRequest(userId1, 100000));
    JsonResponse response1 = checkOutLockClient.attemptCreate(checkOutLock2);
    assertThat(response1.getStatusCode(), is(HttpStatus.SC_SERVICE_UNAVAILABLE));
    assertThat(response1.getBody(), is("Unable to acquire lock"));

    JsonObject checkOutLock3 = toJsonObject(createCheckoutLockRequest("", 1000));
    JsonResponse response2 = checkOutLockClient.attemptCreate(checkOutLock3);
    assertThat(response2.getStatusCode(), is(HttpStatus.SC_UNPROCESSABLE_ENTITY));

    String userId2 = UUID.randomUUID().toString();
    JsonObject checkOutLock4 = toJsonObject(createCheckoutLockRequest(userId2, 1000));
    checkOutLockClient.create(checkOutLock4);

    JsonObject checkOutLock5 = toJsonObject(createCheckoutLockRequest(userId2, 0));
    checkOutLockClient.create(checkOutLock5);

  }

  @SneakyThrows
  @Test
  void canGetCheckOutLock() {
    String userId1 = UUID.randomUUID().toString();
    JsonObject checkOutLock1 = toJsonObject(createCheckoutLockRequest(userId1, 1000));
    JsonResponse response = checkOutLockClient.attemptCreate(checkOutLock1);
    assertThat(response.getStatusCode(),is(HttpStatus.SC_CREATED));

    String id = response.getJson().getString("id");
    JsonResponse response1 = checkOutLockClient.attemptGetById(UUID.fromString(id));
    assertThat(response1.getStatusCode(),is(HttpStatus.SC_OK));
    assertThat(response1.getJson(),is(response.getJson()));

    JsonResponse response2 = checkOutLockClient.attemptGetById("abcd");
    assertThat(response2.getStatusCode(),is(HttpStatus.SC_BAD_REQUEST));
    assertThat(response2.getBody(), is("Invalid lock id"));

  }

  @SneakyThrows
  @Test
  void canGetCheckoutLocksByQueryParams() {
    String userId1 = UUID.randomUUID().toString();
    JsonObject checkOutLock1 = toJsonObject(createCheckoutLockRequest(userId1, 1000));
    JsonResponse response1 = checkOutLockClient.attemptCreate(checkOutLock1);
    assertThat(response1.getStatusCode(),is(HttpStatus.SC_CREATED));

    String userId2 = UUID.randomUUID().toString();
    JsonObject checkOutLock2 = toJsonObject(createCheckoutLockRequest(userId2, 1000));
    JsonResponse response2 = checkOutLockClient.attemptCreate(checkOutLock2);
    assertThat(response2.getStatusCode(),is(HttpStatus.SC_CREATED));

    MultipleRecords<JsonObject> response = checkOutLockClient.getManyWithQueryParams(userId1,0,10);
    assertThat(response.getTotalRecords(), is(1));

  }

  @SneakyThrows
  @Test
  void canDeleteCheckOutLock() {
    String userId1 = UUID.randomUUID().toString();
    JsonObject checkOutLock1 = toJsonObject(createCheckoutLockRequest(userId1, 1000));
    JsonResponse response1 = checkOutLockClient.attemptCreate(checkOutLock1);

    String id = response1.getJson().getString("id");
    checkOutLockClient.deleteById(UUID.fromString(id));

    JsonObject checkOutLock2 = toJsonObject(createCheckoutLockRequest(userId1, 100000));
    JsonResponse response2 = checkOutLockClient.attemptCreate(checkOutLock2);
    assertThat(response2.getStatusCode(), is(HttpStatus.SC_CREATED));

    TextResponse response3 = checkOutLockClient.attemptDeleteById("1234");
    assertThat(response3.getStatusCode(), is(HttpStatus.SC_BAD_REQUEST));
    assertThat(response3.getBody(), is("Invalid lock id"));
  }

  private CheckoutLockRequest createCheckoutLockRequest(String userId, int ttlMs) {
    return new CheckoutLockRequest()
      .withTtlMs(ttlMs)
      .withUserId(userId);
  }

  private JsonObject toJsonObject(CheckoutLockRequest checkoutLockRequest)
    throws JsonProcessingException {
    return new JsonObject(objectMapper.writeValueAsString(checkoutLockRequest));
  }

}
