package org.folio.service.event.handler.processor;

import static java.lang.String.format;
import static org.folio.service.event.InventoryEventType.INVENTORY_SERVICE_POINT_UPDATED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestPolicyRepository;
import org.folio.rest.jaxrs.model.AllowedServicePoints;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.RequestType;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.GroupedCriterias;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ServicePointUpdateProcessorForRequestPolicy extends UpdateEventProcessor<RequestPolicy> {
  private static final Logger log = LogManager.getLogger(ServicePointUpdateProcessorForRequestPolicy.class);
  private static final String SERVICE_POINT_PICKUP_LOCATION = "pickupLocation";

  private final Context context;

  public ServicePointUpdateProcessorForRequestPolicy(Context context) {
    super(INVENTORY_SERVICE_POINT_UPDATED);
    this.context = context;
  }

  @Override
  protected List<Change<RequestPolicy>> collectRelevantChanges(JsonObject payload) {
    JsonObject oldObject = payload.getJsonObject("old");
    JsonObject newObject = payload.getJsonObject("new");
    List<Change<RequestPolicy>> changes = new ArrayList<>();

    boolean isOldServicePointPickupLocation = oldObject.getBoolean(SERVICE_POINT_PICKUP_LOCATION);
    boolean isNewServicePointPickupLocation = newObject.getBoolean(SERVICE_POINT_PICKUP_LOCATION);
    if (!isNewServicePointPickupLocation && isOldServicePointPickupLocation) {
      log.info("collectRelevantChanges:: pickupLocation was changed from true to false");
      changes.add(new Change<>(requestPolicy -> {}));
    }

    return changes;
  }

  @Override
  protected Future<List<RequestPolicy>> applyChanges(List<Change<RequestPolicy>> changes,
    CaseInsensitiveMap<String, String> headers, JsonObject payload) {

    log.debug("applyChanges:: applying searchIndex.pickupServicePointName changes");

    return removeAllowedServicePointFromRelativeRequestPolicies(payload.getJsonObject("old")
      .getString("id"), headers);
  }

  private Future<List<RequestPolicy>> removeAllowedServicePointFromRelativeRequestPolicies(
    String servicePointId, CaseInsensitiveMap<String, String> headers) {

    RequestPolicyRepository requestPolicyRepository = new RequestPolicyRepository(
      context, headers);

    return findRequestPoliciesByServicePointId(requestPolicyRepository, servicePointId)
      .compose(requestPolicies -> removeAllowedServicePoints(requestPolicyRepository,
        requestPolicies, servicePointId));
  }

  private Future<List<RequestPolicy>> findRequestPoliciesByServicePointId(
    RequestPolicyRepository policyRepository, String servicePointId) {

    log.debug("findRequestPoliciesByServicePointId:: fetching requestPolicies for " +
      "servicePointId {}", servicePointId);

    final List<Criteria> criteriaList = Arrays.stream(RequestType.values())
      .map(requestType -> new Criteria()
        .addField("'allowedServicePoints'")
        .addField(format("'%s'", requestType.value()))
        .setOperation("@>")
        .setJSONB(true)
        .setVal(String.format("[\"%s\"]", servicePointId)))
      .toList();
    GroupedCriterias groupedCriterias = new GroupedCriterias();
    criteriaList.forEach(criteria -> groupedCriterias.addCriteria(criteria, "OR"));

    return policyRepository.get(new Criterion().addGroupOfCriterias(groupedCriterias));
  }

  private Future<List<RequestPolicy>> removeAllowedServicePoints(RequestPolicyRepository policyRepository,
                                                                 List<RequestPolicy> requestPolicies, String servicePointId) {

    log.debug("removeAllowedServicePoints:: parameters requestPolicies: {}, servicePointId: {}",
      requestPolicies::size, () -> servicePointId);

    List<RequestPolicy> updatedRequestPolicies = requestPolicies.stream()
      .map(policy -> removeServicePointId(policy, servicePointId))
      .toList();

    return policyRepository.update(updatedRequestPolicies)
      .map(requestPolicies);
  }

  private RequestPolicy removeServicePointId(RequestPolicy policy, String servicePointId) {
    log.debug("removeServicePointId:: parameters policy: {}, servicePointId: {}",
      () -> policy, () -> servicePointId);

    AllowedServicePoints allowedServicePoints = policy.getAllowedServicePoints();

    Set<String> holdAllowedServicePoints = allowedServicePoints.getHold();
    if (holdAllowedServicePoints != null && !holdAllowedServicePoints.isEmpty()) {
      log.info("removeServicePointId:: removing allowed servicePointId from Hold");
      holdAllowedServicePoints.remove(servicePointId);
    }

    Set<String> pageAllowedServicePoints = allowedServicePoints.getPage();
    if (pageAllowedServicePoints != null && !pageAllowedServicePoints.isEmpty()) {
      log.info("removeServicePointId:: removing allowed servicePointId from Page");
      pageAllowedServicePoints.remove(servicePointId);
    }

    Set<String> recallAllowedServicePoints = allowedServicePoints.getRecall();
    if (recallAllowedServicePoints != null && !recallAllowedServicePoints.isEmpty()) {
      log.info("removeServicePointId:: removing allowed servicePointId from Recall");
      recallAllowedServicePoints.remove(servicePointId);
    }
    log.info("removeServicePointId:: result: {}", policy);

    return policy;
  }
}
