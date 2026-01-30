package org.folio.rest.support.matchers;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.okapi.common.XOkapiHeaders.USER_ID;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.storageUrl;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getCheckInEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getCirculationRulesEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstLoanEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstRequestQueueReorderingEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastCheckInEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastCirculationRulesEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastLoanEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastRequestEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLoanEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getRequestEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getRequestQueueReorderingEvents;
import static org.folio.rest.support.matchers.UUIDMatchers.hasUUIDFormat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.folio.rest.jaxrs.model.RequestQueueReordering;
import org.folio.service.event.DomainEventType;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;
import lombok.SneakyThrows;

public final class DomainEventAssertions {

  private static final String NULL_ID = "00000000-0000-0000-0000-000000000000";
  private static final String FOLIO_TENANT_ID = "folio.tenantId";

  private DomainEventAssertions() { }

  public static void assertCreateEventForLoan(JsonObject loan) {
    final String loanId = loan.getString("id");

    await().until(() -> getLoanEvents(loanId).size(), greaterThan(0));

    assertCreateEvent(getFirstLoanEvent(loanId), loan);
  }

  public static void assertUpdateEventForLoan(JsonObject oldLoan, JsonObject newLoan) {
    final String loanId = oldLoan.getString("id");

    await().until(() -> getLoanEvents(loanId).size(), greaterThan(0));

    assertUpdateEvent(getLastLoanEvent(loanId), oldLoan, newLoan);
  }

  public static void assertRemoveEventForLoan(JsonObject loan) {
    final String loanId = loan.getString("id");

    await().until(() -> getLoanEvents(loanId).size(), greaterThan(0));

    assertRemoveEvent(getLastLoanEvent(loanId), loan);
  }

  public static void assertRemoveAllEventForLoan() {
    await().until(() -> getLoanEvents(NULL_ID).size(), greaterThan(0));

    assertRemoveAllEvent(getLastLoanEvent(NULL_ID));
  }

  public static void assertNoLoanEvent(String loanId) {
    await().during(1, SECONDS)
        .until(() -> getLoanEvents(loanId), is(empty()));
  }

  public static void assertLoanEventCount(String loanId, int expectedCount) {
    await().until(() -> getLoanEvents(loanId).size(), greaterThan(0));

    assertThat(getLoanEvents(loanId).size(), is(expectedCount));
  }

  public static void assertCreateEventForCheckIn(JsonObject checkIn) {
    final String checkInId = checkIn.getString("id");

    await().until(() -> getCheckInEvents(checkInId).size(), greaterThan(0));

    assertCreateEvent(getLastCheckInEvent(checkInId), checkIn);
  }

  public static void assertNoCheckInEvent(String checkInId) {
    await().during(1, SECONDS)
        .until(() -> getCheckInEvents(checkInId), is(empty()));
  }

  public static void assertCreateEventForRequest(JsonObject request) {
    final String requestId = request.getString("id");

    await().until(() -> getRequestEvents(requestId).size(), greaterThan(0));

    assertCreateEvent(getLastRequestEvent(requestId), request);
  }

  public static void assertRequestQueueReorderingEvent(String instanceId, String itemId,
    List<String> requestIds, RequestQueueReordering.RequestLevel requestLevel) {

    await().until(() -> getRequestQueueReorderingEvents().size(), greaterThan(0));

    JsonObject payload = new JsonObject()
      .put("instanceId", instanceId)
      .put("itemId", itemId)
      .put("requestLevel", requestLevel.value())
      .put("requestIds", new JsonArray(requestIds));

    assertCreateEvent(getFirstRequestQueueReorderingEvent(), payload);
  }

  public static void assertNoRequestEvent(String requestId) {
    await().during(1, SECONDS)
      .until(() -> getRequestEvents(requestId), is(empty()));
  }

  public static void assertNoUpdateEventForRequest(String requestId) {
    await().during(1, SECONDS)
      .until(() -> getRequestEvents(requestId).stream()
        .filter(event -> DomainEventType.UPDATED.name().equals(
          event.value().getString("type")))
        .toList(), is(empty()));
  }

  public static void assertUpdateEventForRequest(JsonObject oldRequest, JsonObject newRequest) {
    final String requestId = oldRequest.getString("id");

    await().until(() -> getRequestEvents(requestId).size(), greaterThan(0));

    assertUpdateEvent(getLastRequestEvent(requestId), oldRequest, newRequest);
  }

  public static void assertUpdateEventForCirculationRules(JsonObject oldRules, JsonObject newRules) {
    await().until(() -> getCirculationRulesEvents().size(), greaterThan(0));
    assertUpdateCirculationRulesEvent(getLastCirculationRulesEvent(), oldRules, newRules);
  }

  public static void assertRemoveEventForRequest(JsonObject request) {
    final String requestId = request.getString("id");

    await().until(() -> getRequestEvents(requestId).size(), greaterThan(0));

    assertRemoveEvent(getLastRequestEvent(requestId), request);
  }

  private static ConditionFactory await() {
    return Awaitility.await().atMost(5, SECONDS);
  }

  private static void assertCreateEvent(KafkaConsumerRecord<String, JsonObject> createEvent, JsonObject newRecord) {
    assertThat("Create event should be present", createEvent.value(), is(notNullValue()));
    assertBasicEventFields(createEvent, DomainEventType.CREATED);

    assertThat(getOldValue(createEvent), nullValue());
    assertThat(getNewValue(createEvent), is(newRecord));

    assertHeaders(createEvent.headers());
  }

  private static void assertUpdateEvent(KafkaConsumerRecord<String, JsonObject> updateEvent,
      JsonObject oldRecord, JsonObject newRecord) {
    assertThat("Update event should be present", updateEvent.value(), is(notNullValue()));
    assertBasicEventFields(updateEvent, DomainEventType.UPDATED);

    assertThat(getOldValue(updateEvent), is(oldRecord));
    assertThat(getNewValue(updateEvent), is(newRecord));

    assertHeaders(updateEvent.headers());
  }

  private static void assertUpdateCirculationRulesEvent(KafkaConsumerRecord<String,
    JsonObject> updateEvent, JsonObject oldRecord, JsonObject newRecord) {
    assertThat("Update event should be present", updateEvent.value(), is(notNullValue()));
    assertBasicEventFields(updateEvent, DomainEventType.UPDATED);

    JsonObject oldValue = getOldValue(updateEvent);
    JsonObject newValue = getNewValue(updateEvent);
    assertJsonEqualsExceptMetadata(oldValue, oldRecord);
    assertJsonEqualsExceptMetadata(newValue, newRecord);

    assertHeaders(updateEvent.headers());
  }

  private static void assertJsonEqualsExceptMetadata(JsonObject actual, JsonObject expected) {
    JsonObject actualCopy = actual.copy();
    JsonObject expectedCopy = expected.copy();
    actualCopy.remove("metadata");
    expectedCopy.remove("metadata");
    assertThat(actualCopy, is(expectedCopy));
  }

  private static void assertRemoveEvent(KafkaConsumerRecord<String, JsonObject> deleteEvent, JsonObject record) {
    assertThat("Delete event should be present", deleteEvent.value(), is(notNullValue()));
    assertBasicEventFields(deleteEvent, DomainEventType.DELETED);

    assertThat(getNewValue(deleteEvent), nullValue());
    assertThat(getOldValue(deleteEvent), is(record));

    assertHeaders(deleteEvent.headers());
  }

  private static void assertRemoveAllEvent(KafkaConsumerRecord<String, JsonObject> deleteEvent) {
    assertThat("Delete All event should be present", deleteEvent.value(), is(notNullValue()));
    assertBasicEventFields(deleteEvent, DomainEventType.ALL_DELETED);

    assertThat(getNewValue(deleteEvent), nullValue());
    assertThat(getOldValue(deleteEvent), nullValue());

    assertHeaders(deleteEvent.headers());
  }

  private static void assertBasicEventFields(KafkaConsumerRecord<String, JsonObject> event,
      DomainEventType expectedType) {
    JsonObject value = event.value();

    assertThat(value.getString("id"), hasUUIDFormat());
    assertThat(value.getString("type"), is(expectedType.name()));
    assertThat(value.getString("tenant"), is(TENANT_ID));
    assertThat(value.getLong("timestamp"), is(notNullValue()));
  }

  @SneakyThrows
  private static void assertHeaders(List<KafkaHeader> headers) {
    final MultiMap caseInsensitiveMap = caseInsensitiveMultiMap()
        .addAll(kafkaHeadersToMap(headers));

    assertEquals(4, caseInsensitiveMap.size());
    assertEquals(TENANT_ID, caseInsensitiveMap.get(TENANT));
    assertEquals(storageUrl("").toString(), caseInsensitiveMap.get(URL));
    assertEquals(TENANT_ID, caseInsensitiveMap.get(FOLIO_TENANT_ID));
    assertNotNull(USER_ID);
  }

  private static JsonObject getOldValue(KafkaConsumerRecord<String, JsonObject> event) {
    return getDataValue(event, "old");
  }

  private static JsonObject getNewValue(KafkaConsumerRecord<String, JsonObject> event) {
    return getDataValue(event, "new");
  }

  private static JsonObject getDataValue(KafkaConsumerRecord<String, JsonObject> event, String field) {
    return event.value().getJsonObject("data", new JsonObject()).getJsonObject(field);
  }

}
