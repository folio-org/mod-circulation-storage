package org.folio.rest.support.matchers;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.storageUrl;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getCheckInEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstCheckInEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstLoanEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastLoanEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLoanEvents;
import static org.folio.rest.support.matchers.UUIDMatchers.hasUUIDFormat;

import java.util.List;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import org.folio.service.event.DomainEventType;

public final class DomainEventAssertions {

  private static final String NULL_ID = "00000000-0000-0000-0000-000000000000";

  private DomainEventAssertions() { }


  public static void assertCreateEventForLoan(JsonObject loan) {
    final String loanId = loan.getString("id");

    await().until(() -> getLoanEvents(loanId).size(), greaterThan(0));

    assertCreateEvent(getFirstLoanEvent(loanId), loan);
  }

  public static void assertUpdateEventForLoan(JsonObject oldLoan, JsonObject newLoan) {
    final String loanId = oldLoan.getString("id");

    await().until(() -> getLoanEvents(loanId).size(), greaterThan(1));

    assertUpdateEvent(getLastLoanEvent(loanId), oldLoan, newLoan);
  }

  public static void assertRemoveEventForLoan(JsonObject loan) {
    final String loanId = loan.getString("id");

    await().until(() -> getLoanEvents(loanId).size(), greaterThan(1));

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

  public static void assertCreateEventForCheckIn(JsonObject checkIn) {
    final String checkInId = checkIn.getString("id");

    await().until(() -> getCheckInEvents(checkInId).size(), greaterThan(0));

    assertCreateEvent(getFirstCheckInEvent(checkInId), checkIn);
  }

  public static void assertNoCheckInEvent(String checkInId) {
    await().during(1, SECONDS)
      .until(() -> getCheckInEvents(checkInId), is(empty()));
  }

  private static ConditionFactory await() {
    return Awaitility.await().atMost(5, SECONDS);
  }

  private static void assertCreateEvent(KafkaConsumerRecord<String, JsonObject> createEvent, JsonObject newRecord) {
    assertThat("Create event should be present", createEvent.value(), is(notNullValue()));
    assertBasicEventFields(createEvent, DomainEventType.CREATED);

    JsonObject data = createEvent.value().getJsonObject("data");
    assertThat(data.getJsonObject("old"), nullValue());
    assertThat(data.getJsonObject("new"), is(newRecord));

    assertHeaders(createEvent.headers());
  }

  private static void assertUpdateEvent(KafkaConsumerRecord<String, JsonObject> updateEvent,
      JsonObject oldRecord, JsonObject newRecord) {
    assertThat("Update event should be present", updateEvent.value(), is(notNullValue()));
    assertBasicEventFields(updateEvent, DomainEventType.UPDATED);

    JsonObject data = updateEvent.value().getJsonObject("data");
    assertThat(data.getJsonObject("old"), is(oldRecord));
    assertThat(data.getJsonObject("new"), is(newRecord));

    assertHeaders(updateEvent.headers());
  }

  private static void assertBasicEventFields(KafkaConsumerRecord<String, JsonObject> event,
      DomainEventType expectedType) {
    JsonObject value = event.value();

    assertThat(value.getString("id"), hasUUIDFormat());
    assertThat(value.getString("type"), is(expectedType.name()));
    assertThat(value.getString("tenant"), is(TENANT_ID));
    assertThat(value.getLong("timestamp"), is(notNullValue()));
  }

  private static void assertRemoveEvent(KafkaConsumerRecord<String, JsonObject> deleteEvent, JsonObject record) {
    assertThat("Delete event should be present", deleteEvent.value(), is(notNullValue()));
    assertBasicEventFields(deleteEvent, DomainEventType.DELETED);

    JsonObject data = deleteEvent.value().getJsonObject("data");
    assertThat(data.getJsonObject("new"), nullValue());
    assertThat(data.getJsonObject("old"), is(record));

    assertHeaders(deleteEvent.headers());
  }

  private static void assertRemoveAllEvent(KafkaConsumerRecord<String, JsonObject> deleteEvent) {
    assertThat("Delete All event should be present", deleteEvent.value(), is(notNullValue()));
    assertBasicEventFields(deleteEvent, DomainEventType.ALL_DELETED);

    JsonObject data = deleteEvent.value().getJsonObject("data");
    assertThat(data.getJsonObject("new"), nullValue());
    assertThat(deleteEvent.value().getJsonObject("old"), nullValue());

    assertHeaders(deleteEvent.headers());
  }

  @SneakyThrows
  private static void assertHeaders(List<KafkaHeader> headers) {
    final MultiMap caseInsensitiveMap = caseInsensitiveMultiMap()
        .addAll(kafkaHeadersToMap(headers));

    assertEquals(2, caseInsensitiveMap.size());
    assertEquals(TENANT_ID, caseInsensitiveMap.get(TENANT));
    assertEquals(storageUrl("").toString(), caseInsensitiveMap.get(URL));
  }

}
