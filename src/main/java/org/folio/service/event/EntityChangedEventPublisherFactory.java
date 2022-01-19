package org.folio.service.event;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.support.Environment.environmentName;

import java.util.Map;

import io.vertx.core.Context;

import org.folio.persist.LoanRepository;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.service.kafka.topic.KafkaTopic;

public class EntityChangedEventPublisherFactory {

  private static final String NULL_ID = "00000000-0000-0000-0000-000000000000";

  private EntityChangedEventPublisherFactory() {
  }

  public static EntityChangedEventPublisher<String, Loan> loanEventPublisher(
      Context vertxContext, Map<String, String> okapiHeaders) {

    return new EntityChangedEventPublisher<>(okapiHeaders, Loan::getId, NULL_ID,
        new EntityChangedEventFactory<>(),
        new DomainEventPublisher<>(vertxContext,
            KafkaTopic.loan(tenantId(okapiHeaders), environmentName()),
            FailureHandler.noOperation()),
        new LoanRepository(vertxContext, okapiHeaders));
  }

}
