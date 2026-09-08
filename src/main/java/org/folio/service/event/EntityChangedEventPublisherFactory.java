package org.folio.service.event;

import static org.folio.support.kafka.topic.CirculationStorageKafkaTopic.CHECK_IN;
import static org.folio.support.kafka.topic.CirculationStorageKafkaTopic.CIRCULATION_SETTINGS;
import static org.folio.support.kafka.topic.CirculationStorageKafkaTopic.LOAN;
import static org.folio.support.kafka.topic.CirculationStorageKafkaTopic.REQUEST;
import static org.folio.support.kafka.topic.CirculationStorageKafkaTopic.REQUEST_QUEUE_REORDERING;
import static org.folio.support.kafka.topic.CirculationStorageKafkaTopic.RULES;

import java.util.Map;

import org.folio.persist.CheckInRepository;
import org.folio.persist.CirculationRulesRepository;
import org.folio.persist.CirculationSettingsRepository;
import org.folio.persist.LoanRepository;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.CheckIn;
import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.rest.jaxrs.model.CirculationSetting;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.RequestQueueReordering;

import io.vertx.core.Context;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class EntityChangedEventPublisherFactory {

  private static final String NULL_ID = "00000000-0000-0000-0000-000000000000";

  private EntityChangedEventPublisherFactory() {
  }

  public static EntityChangedEventPublisher<String, Loan> loanEventPublisher(
      Context vertxContext, Map<String, String> okapiHeaders) {

    return new EntityChangedEventPublisher<>(okapiHeaders, Loan::getId, NULL_ID,
        new EntityChangedEventFactory<>(),
        new DomainEventPublisher<>(LOAN, okapiHeaders, vertxContext),
        new LoanRepository(vertxContext, okapiHeaders));
  }

  public static EntityChangedEventPublisher<String, Request> requestEventPublisher(
      Context vertxContext, Map<String, String> okapiHeaders) {

    return new EntityChangedEventPublisher<>(okapiHeaders, Request::getId, NULL_ID,
        new EntityChangedEventFactory<>(),
        new DomainEventPublisher<>(REQUEST, okapiHeaders, vertxContext),
        new RequestRepository(vertxContext, okapiHeaders));
  }

  public static EntityChangedEventPublisher<String, RequestQueueReordering>
  requestBatchEventPublisher(Context vertxContext, Map<String, String> okapiHeaders) {

    return new EntityChangedEventPublisher<>(okapiHeaders, RequestQueueReordering::getInstanceId,
      NULL_ID, new EntityChangedEventFactory<>(), new DomainEventPublisher<>(
        REQUEST_QUEUE_REORDERING, okapiHeaders, vertxContext), null);
  }

  public static EntityChangedEventPublisher<String, CheckIn> checkInEventPublisher(
      Context vertxContext, Map<String, String> okapiHeaders) {

    return new EntityChangedEventPublisher<>(okapiHeaders, CheckIn::getId, NULL_ID,
        new EntityChangedEventFactory<>(),
        new DomainEventPublisher<>(CHECK_IN, okapiHeaders, vertxContext),
        new CheckInRepository(vertxContext, okapiHeaders));
  }

  public static EntityChangedEventPublisher<String, CirculationRules> circulationRulesEventPublisher(
    Context vertxContext, Map<String, String> okapiHeaders) {

    log.info("circulationRulesEventPublisher:: creating circulation rules event publisher");

    return new EntityChangedEventPublisher<>(okapiHeaders, CirculationRules::getId, NULL_ID,
      new EntityChangedEventFactory<>(),
      new DomainEventPublisher<>(RULES, okapiHeaders, vertxContext),
      new CirculationRulesRepository(vertxContext, okapiHeaders));
  }

  public static EntityChangedEventPublisher<String, CirculationSetting> circulationSettingsEventPublisher(
    Context vertxContext, Map<String, String> okapiHeaders) {

    return new EntityChangedEventPublisher<>(okapiHeaders, CirculationSetting::getId, NULL_ID,
      new EntityChangedEventFactory<>(),
      new DomainEventPublisher<>(CIRCULATION_SETTINGS, okapiHeaders, vertxContext),
      new CirculationSettingsRepository(vertxContext, okapiHeaders));
  }

}
