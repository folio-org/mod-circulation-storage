package org.folio.service.event.handler;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.requireNonNullElse;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.support.LoanAnonymizationPreference.LIBRARY_POLICY;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.EnumUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.persist.UserLoanAnonymizationPreferenceRepository;
import org.folio.service.event.DomainEventType;
import org.folio.support.LoanAnonymizationPreference;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Keeps the users' loan anonymization preferences in sync with the user domain events of mod-users.
 */
public class UserEventHandler implements AsyncRecordHandler<String, String> {
  private static final Logger log = LogManager.getLogger(UserEventHandler.class);

  private static final int QUERY_TIMEOUT_MILLIS = 60_000;

  private final Context context;
  private final int queryTimeoutMillis;
  private final Map<String, Future<Void>> lastQueuedEventByKey = new ConcurrentHashMap<>();

  public UserEventHandler(Context context) {
    this(context, QUERY_TIMEOUT_MILLIS);
  }

  public UserEventHandler(Context context, int queryTimeoutMillis) {
    this.context = context;
    this.queryTimeoutMillis = queryTimeoutMillis;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    // KafkaConsumerWrapper calls handle() for the next record before the future returned for the
    // previous record has completed (up to its load limit of records at a time). Without this
    // queue, two events of the same user could be processed concurrently and the older one could
    // be stored last. The order only matters per user, so there is one queue per tenant topic and
    // record key (the user id); a queue is removed when its last event has completed.
    String queueKey = kafkaConsumerRecord.topic() + "/" + kafkaConsumerRecord.key();
    Future<Void> event = lastQueuedEventByKey.compute(queueKey, (key, previous) ->
      requireNonNullElse(previous, succeededFuture())
        .transform(ignored -> handleEvent(kafkaConsumerRecord)));
    event.onComplete(ignored -> lastQueuedEventByKey.remove(queueKey, event));
    return event.map(kafkaConsumerRecord.key());
  }

  private Future<Void> handleEvent(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    CaseInsensitiveMap<String, String> headers =
      new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));
    UserLoanAnonymizationPreferenceRepository repository =
      new UserLoanAnonymizationPreferenceRepository(context, headers, queryTimeoutMillis);
    JsonObject event = new JsonObject(kafkaConsumerRecord.value());
    JsonObject data = event.getJsonObject("data", new JsonObject());

    return switch (EnumUtils.getEnum(DomainEventType.class, event.getString("type"))) {
      case CREATED, UPDATED -> syncPreference(data.getJsonObject("old"),
        data.getJsonObject("new"), repository);
      case DELETED -> deletePreference(data.getJsonObject("old"), repository);
      case null, default -> succeededFuture();
    };
  }

  private static Future<Void> syncPreference(JsonObject oldUser, JsonObject newUser,
    UserLoanAnonymizationPreferenceRepository repository) {

    if (newUser == null) {
      log.warn("syncPreference:: event without new user version, ignoring it");
      return succeededFuture();
    }

    String newValue = getPreferenceValueOrLibraryPolicy(newUser);
    if (newValue.equals(getPreferenceValueOrLibraryPolicy(oldUser))) {
      return succeededFuture();
    }

    String userId = newUser.getString("id");
    Optional<LoanAnonymizationPreference> newPreference =
      LoanAnonymizationPreference.fromValue(newValue);
    if (newPreference.isEmpty()) {
      log.warn("syncPreference:: unknown preference {} of user {}, ignoring it", newValue, userId);
      return succeededFuture();
    }
    if (newPreference.get().deviatesFromLibraryPolicy()) {
      log.info("syncPreference:: user {} changed the preference to {}", userId, newValue);
      return repository.save(userId, newPreference.get());
    }

    log.info("syncPreference:: user {} follows the library policy again", userId);
    return repository.delete(userId);
  }

  private static Future<Void> deletePreference(JsonObject deletedUser,
    UserLoanAnonymizationPreferenceRepository repository) {

    if (deletedUser == null) {
      log.warn("deletePreference:: event without deleted user, ignoring it");
      return succeededFuture();
    }

    String userId = deletedUser.getString("id");
    log.info("deletePreference:: user {} was deleted", userId);
    return repository.delete(userId);
  }

  private static String getPreferenceValueOrLibraryPolicy(JsonObject user) {
    String value = user == null ? null : user.getString("loanAnonymizationPreference");
    return requireNonNullElse(value, LIBRARY_POLICY.value());
  }
}
