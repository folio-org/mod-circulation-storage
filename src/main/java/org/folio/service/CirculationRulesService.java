package org.folio.service;

import static org.folio.service.event.EntityChangedEventPublisherFactory.circulationRulesEventPublisher;

import java.util.Map;

import org.folio.persist.CirculationRulesRepository;
import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.service.event.EntityChangedEventPublisher;

import io.vertx.core.Context;
import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CirculationRulesService {

  private final CirculationRulesRepository repository;
  private final EntityChangedEventPublisher<String, CirculationRules> eventPublisher;

  public CirculationRulesService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.repository = new CirculationRulesRepository(vertxContext, okapiHeaders);
    this.eventPublisher = circulationRulesEventPublisher(vertxContext, okapiHeaders);
  }

  public Future<Void> update(CirculationRules newRules) {
    log.info("updateCirculationRules:: updating circulation rules");
    log.debug("updateCirculationRules:: new rules: {}", newRules::getRulesAsText);

    return repository.get()
      .compose(oldRules -> repository.update(newRules)
        .compose(updatedRules -> eventPublisher.publishUpdated(oldRules.getId(), oldRules, updatedRules)))
      .onSuccess(ignored -> log.info("update:: circulation rules updated"))
      .onFailure(t -> log.error("update:: circulation rules update failed", t));
  }

}
