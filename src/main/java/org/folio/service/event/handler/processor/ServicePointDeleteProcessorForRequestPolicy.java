package org.folio.service.event.handler.processor;

import static org.folio.service.event.InventoryEventType.INVENTORY_SERVICE_POINT_DELETED;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestPolicyRepository;
import org.folio.rest.jaxrs.model.AllowedServicePoints;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.RequestType;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ServicePointDeleteProcessorForRequestPolicy
  extends DeleteEventProcessor<RequestPolicy> {

  private static final Logger log = LogManager.getLogger(ServicePointDeleteProcessorForRequestPolicy.class);

  private final Context context;

  public ServicePointDeleteProcessorForRequestPolicy(Context context) {
    super(INVENTORY_SERVICE_POINT_DELETED);
    this.context = context;
  }

  @Override
  protected List<Change<RequestPolicy>> collectRelevantChanges(JsonObject payload) {
    log.debug("collectRelevantChanges:: payload: {}", payload);

    JsonObject oldObject = payload.getJsonObject("old");

    List<Change<RequestPolicy>> changes = new ArrayList<>();
    String deletedServicePointId = oldObject.getString("id");

    changes.add(new Change<>(requestPolicy -> {
      AllowedServicePoints allowedServicePoints = requestPolicy.getAllowedServicePoints();

      removeAllowedServicePoint(deletedServicePointId, requestPolicy, RequestType.HOLD,
        allowedServicePoints::getHold, allowedServicePoints::setHold);
      removeAllowedServicePoint(deletedServicePointId, requestPolicy, RequestType.PAGE,
        allowedServicePoints::getPage, allowedServicePoints::setPage);
      removeAllowedServicePoint(deletedServicePointId, requestPolicy, RequestType.RECALL,
        allowedServicePoints::getRecall, allowedServicePoints::setRecall);

      if ((allowedServicePoints.getHold() == null || allowedServicePoints.getHold().isEmpty())
      && (allowedServicePoints.getPage() == null || allowedServicePoints.getPage().isEmpty())
      && (allowedServicePoints.getRecall() == null || allowedServicePoints.getRecall().isEmpty())) {
        requestPolicy.setAllowedServicePoints(null);
      }
    }));

    return changes;
  }

  private void removeAllowedServicePoint(String deletedServicePointId, RequestPolicy requestPolicy,
    RequestType requestType, Supplier<Set<String>> getAllowedServicePointsSupplier,
    Consumer<Set<String>> setAllowedServicePointsConsumer) {

    log.debug("removeAllowedServicePoint:: deletedServicePointId={}, requestPolicy={}, " +
        "requestType={}", deletedServicePointId, requestPolicy, requestType);

    Set<String> allowedServicePoints = getAllowedServicePointsSupplier.get();

    if (allowedServicePoints == null) {
      log.info("removeAllowedServicePoint:: allowed service points missing for type {}",
        requestType);
      return;
    }

    allowedServicePoints.remove(deletedServicePointId);
    if (allowedServicePoints.isEmpty()) {
      log.info("removeAllowedServicePoint:: request policy ID={}: 0 allowed service point for {} " +
        "type, removing it from the policy", requestPolicy::getId, () -> requestType);

      setAllowedServicePointsConsumer.accept(null);
      requestPolicy.getRequestTypes().remove(requestType);
    }
  }

  @Override
  protected Future<List<RequestPolicy>> applyChanges(List<Change<RequestPolicy>> changes,
    CaseInsensitiveMap<String, String> headers, JsonObject payload) {

    log.debug("applyChanges:: applying searchIndex.pickupServicePointName changes");

    JsonObject oldObject = payload.getJsonObject("old");
    RequestPolicyRepository requestPolicyRepository = new RequestPolicyRepository(context,
      headers);

    return requestPolicyRepository.findByServicePointId(oldObject.getString("id"))
      .compose(policies -> applyDbUpdates(policies, changes, requestPolicyRepository));
  }
}
