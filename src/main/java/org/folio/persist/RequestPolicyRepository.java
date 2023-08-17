package org.folio.persist;

import static java.lang.String.format;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.REQUEST_POLICY_CLASS;
import static org.folio.support.ModuleConstants.REQUEST_POLICY_TABLE;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.RequestType;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.GroupedCriterias;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class RequestPolicyRepository extends AbstractRepository<RequestPolicy> {

  private static final Logger log = LogManager.getLogger(RequestPolicyRepository.class);

  public RequestPolicyRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), REQUEST_POLICY_TABLE, REQUEST_POLICY_CLASS);
  }

  public Future<List<RequestPolicy>> findByServicePointId(String servicePointId) {
    log.debug("findRequestPoliciesByServicePointId:: fetching requestPolicies for " +
      "servicePointId {}", servicePointId);

    final List<Criteria> criteriaList = Arrays.stream(RequestType.values())
      .map(requestType -> new Criteria()
        .addField("'allowedServicePoints'")
        .addField(format("'%s'", requestType.value()))
        .setOperation("@>")
        .setJSONB(true)
        .setVal(format("[\"%s\"]", servicePointId)))
      .toList();
    GroupedCriterias groupedCriterias = new GroupedCriterias();
    criteriaList.forEach(criteria -> groupedCriterias.addCriteria(criteria, "OR"));

    return get(new Criterion().addGroupOfCriterias(groupedCriterias));
  }

}