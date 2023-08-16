package org.folio.service.event.handler;

import static java.lang.String.format;
import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.service.event.InventoryEventType.INVENTORY_SERVICE_POINT_UPDATED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestPolicyRepository;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.AllowedServicePoints;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.RequestType;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.GroupedCriterias;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class ServicePointUpdateEventHandler extends UpdateEventAbstractHandler<Request> {
  private static final Logger log = LogManager.getLogger(ServicePointUpdateEventHandler.class);
  private static final String SERVICE_POINT_NAME_KEY = "name";
  private static final String SERVICE_POINT_PICKUP_LOCATION = "pickupLocation";

  private final Context context;

  public ServicePointUpdateEventHandler(Context context) {
    super(INVENTORY_SERVICE_POINT_UPDATED);
    this.context = context;
  }

  @Override
  protected List<Change<Request>> collectRelevantChanges(JsonObject oldObject, JsonObject newObject) {
    List<Change<Request>> changes = new ArrayList<>();

    // compare service point names
    String oldServicePointName = oldObject.getString(SERVICE_POINT_NAME_KEY);
    String newServicePointName = newObject.getString(SERVICE_POINT_NAME_KEY);
    if (notEqual(oldServicePointName, newServicePointName)) {
      log.info("collectRelevantChanges:: changing searchIndex.pickupServicePointName from {} to {}",
        oldServicePointName, newServicePointName);
      changes.add(new Change<>(request -> request.getSearchIndex()
        .setPickupServicePointName(newServicePointName)));
    }

    return changes;
  }

  @Override
  protected Future<List<Request>> applyChanges(List<Change<Request>> changes,
    KafkaConsumerRecord<String, String> event, JsonObject oldObject, JsonObject newObject) {

    log.debug("applyChanges:: applying searchIndex.pickupServicePointName changes");

    boolean isOldServicePointPickupLocation = oldObject.getBoolean(SERVICE_POINT_PICKUP_LOCATION);
    boolean isNewServicePointPickupLocation = newObject.getBoolean(SERVICE_POINT_PICKUP_LOCATION);
    if (!isNewServicePointPickupLocation && isOldServicePointPickupLocation) {
      log.info("applyChanges:: pickupLocation was changed from true to false");
      removeAllowedServicePointFromRelativeRequestPolicies(newObject.getString("id"), event);
    }

    RequestRepository requestRepository = new RequestRepository(context, getKafkaHeaders(event));

    return findRequestsByPickupServicePointId(requestRepository, oldObject.getString("id"))
      .compose(requests -> applyDbUpdates(requests, changes, requestRepository));
  }

  private Future<List<Request>> findRequestsByPickupServicePointId(
    RequestRepository requestRepository, String pickupServicePointId) {

    log.info("findRequestsByServicePointId:: fetching requests for pickupServicePointId {}",
      pickupServicePointId);

    return requestRepository.get(new Criterion(
      new Criteria()
        .addField("'pickupServicePointId'")
        .setOperation("=")
        .setVal(pickupServicePointId)));
  }

  private void removeAllowedServicePointFromRelativeRequestPolicies(String servicePointId,
    KafkaConsumerRecord<String, String> event) {

    RequestPolicyRepository requestPolicyRepository = new RequestPolicyRepository(
      postgresClient(context, getKafkaHeaders(event)));

    findRequestPoliciesByServicePointId(requestPolicyRepository, servicePointId)
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

  private Future<Void> removeAllowedServicePoints(RequestPolicyRepository policyRepository,
    List<RequestPolicy> requestPolicies, String servicePointId) {

    log.debug("removeAllowedServicePoints:: parameters requestPolicies: {}, servicePointId: {}",
      requestPolicies::size, () -> servicePointId);

    List<RequestPolicy> updatedRequestPolicies = requestPolicies.stream()
      .map(policy -> removeServicePointId(policy, servicePointId))
      .toList();

    return policyRepository.update(updatedRequestPolicies)
      .mapEmpty();
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
