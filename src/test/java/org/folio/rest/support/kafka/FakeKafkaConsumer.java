package org.folio.rest.support.kafka;

import static io.vertx.kafka.client.consumer.KafkaConsumer.create;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.kafka.services.KafkaEnvironmentProperties;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;

public final class FakeKafkaConsumer {

  private static final String LOAN_TOPIC_NAME = "folio.test_tenant.circulation.loan";
  private static final String REQUEST_TOPIC_NAME = "folio.test_tenant.circulation.request";
  private static final String CHECKIN_TOPIC_NAME = "folio.test_tenant.circulation.check-in";
  private static final String CIRCULATION_RULES_TOPIC_NAME = "folio.test_tenant.circulation.rules";
  private static final String REQUEST_QUEUE_REORDERING_TOPIC_NAME = "folio.test_tenant.circulation.request-queue-reordering";

  private static final Map<String, List<KafkaConsumerRecord<String, JsonObject>>> loanEvents =
      new ConcurrentHashMap<>();
  private static final Map<String, List<KafkaConsumerRecord<String, JsonObject>>> requestEvents =
      new ConcurrentHashMap<>();
  private static final Map<String, List<KafkaConsumerRecord<String, JsonObject>>> checkInEvents =
      new ConcurrentHashMap<>();
  private static final Map<String, List<KafkaConsumerRecord<String, JsonObject>>> circulationRulesEvents =
    new ConcurrentHashMap<>();
  private static final Map<String, List<KafkaConsumerRecord<String, JsonObject>>> requestQueueReorderingEvents =
    new ConcurrentHashMap<>();
  private static final Map<String, Map<String, List<KafkaConsumerRecord<String, JsonObject>>>> topicToEvents = Map.of(
    LOAN_TOPIC_NAME, loanEvents,
    REQUEST_TOPIC_NAME, requestEvents,
    CHECKIN_TOPIC_NAME, checkInEvents,
    CIRCULATION_RULES_TOPIC_NAME, circulationRulesEvents,
    REQUEST_QUEUE_REORDERING_TOPIC_NAME, requestQueueReorderingEvents
  );

  public FakeKafkaConsumer consume(Vertx vertx) {
    final KafkaConsumer<String, JsonObject> consumer = create(vertx, consumerProperties());

    consumer.subscribe(Set.of(LOAN_TOPIC_NAME, REQUEST_TOPIC_NAME, CHECKIN_TOPIC_NAME,
      CIRCULATION_RULES_TOPIC_NAME, REQUEST_QUEUE_REORDERING_TOPIC_NAME));

    consumer.handler(message -> {
      var recordEvents = topicToEvents.get(message.topic());

      if (recordEvents == null) {
        throw new IllegalArgumentException("Undefined topic: " + message.topic());
      }

      var storageList = recordEvents.computeIfAbsent(message.key(), k -> new ArrayList<>());
      storageList.add(message);
    });

    return this;
  }

  public static void removeAllEvents() {
    loanEvents.clear();
    requestEvents.clear();
    checkInEvents.clear();
    circulationRulesEvents.clear();
    requestQueueReorderingEvents.clear();
  }

  public static int getAllPublishedLoanCount() {
    return loanEvents.size();
  }

  public static Collection<KafkaConsumerRecord<String, JsonObject> > getLoanEvents(String loanId) {
    return loanEvents.getOrDefault(loanId, emptyList());
  }

  public static Collection<KafkaConsumerRecord<String, JsonObject> > getRequestEvents(String requestId) {
    return requestEvents.getOrDefault(requestId, emptyList());
  }

  public static Collection<KafkaConsumerRecord<String, JsonObject> > getCheckInEvents(String checkInId) {
    return checkInEvents.getOrDefault(checkInId, emptyList());
  }

  public static Collection<KafkaConsumerRecord<String, JsonObject>> getCirculationRulesEvents() {
    return circulationRulesEvents.values()
      .stream()
      .findFirst()
      .orElseGet(Collections::emptyList);
  }

  public static Collection<KafkaConsumerRecord<String, JsonObject>> getRequestQueueReorderingEvents() {
    return requestQueueReorderingEvents.values()
      .stream()
      .findFirst()
      .orElseGet(Collections::emptyList);
  }

  public static KafkaConsumerRecord<String, JsonObject>  getFirstLoanEvent(String loanId) {
    return getFirstEvent(getLoanEvents(loanId));
  }

  public static KafkaConsumerRecord<String, JsonObject> getLastLoanEvent(String loanId) {
    return getLastEvent(getLoanEvents(loanId));
  }

  public static KafkaConsumerRecord<String, JsonObject> getFirstRequestEvent(String requestId) {
    return getFirstEvent(getRequestEvents(requestId));
  }

  public static KafkaConsumerRecord<String, JsonObject> getFirstCirculationRulesEvent() {
    return getFirstEvent(getCirculationRulesEvents());
  }

  public static KafkaConsumerRecord<String, JsonObject> getLastCirculationRulesEvent() {
    return getLastEvent(getCirculationRulesEvents());
  }

  public static KafkaConsumerRecord<String, JsonObject> getLastRequestEvent(String requestId) {
    return getLastEvent(getRequestEvents(requestId));
  }

  public static KafkaConsumerRecord<String, JsonObject>  getLastCheckInEvent(String checkInId) {
    return getLastEvent(getCheckInEvents(checkInId));
  }

  public static KafkaConsumerRecord<String, JsonObject> getFirstCheckInEvent(String checkInId) {
    return getFirstEvent(getCheckInEvents(checkInId));
  }

  private static KafkaConsumerRecord<String, JsonObject> getFirstEvent(
      Collection<KafkaConsumerRecord<String, JsonObject>> events) {

    return events.stream().findFirst().orElse(null);
  }

  private static KafkaConsumerRecord<String, JsonObject> getLastEvent(
      Collection<KafkaConsumerRecord<String, JsonObject>> events) {

    return events.stream().skip(events.size() - 1).findFirst().orElse(null);
  }

  private Map<String, String> consumerProperties() {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", KafkaEnvironmentProperties.host() + ":" + KafkaEnvironmentProperties.port());
    config.put("key.deserializer", StringDeserializer.class.getName());
    config.put("value.deserializer", JsonObjectDeserializer.class.getName());
    config.put("group.id", "folio_test");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "true");

    return config;
  }

}
