package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.Map;

import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class CirculationRulesRepository extends AbstractRepository<CirculationRules> {
  private static final String CIRCULATION_RULES_TABLE = "circulation_rules";

  public CirculationRulesRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), CIRCULATION_RULES_TABLE, CirculationRules.class);
  }

  public Future<CirculationRules> get() {
    return get(new Criterion())
      .map(rules -> rules.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Circulation rules not found")));
  }

  public Future<CirculationRules> update(CirculationRules newRules) {
    return postgresClient.update(CIRCULATION_RULES_TABLE, newRules, new Criterion(), true)
      .compose(ignored -> get());
  }
}
