package org.folio.service.event.handler.processor.util;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.AllowedServicePoints;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.RequestType;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AllowedServicePointsUtil {
  private static final Logger log = LogManager.getLogger();

  public static void removeServicePointFromRequestPolicy(RequestPolicy requestPolicy,
    String servicePointId) {

    log.info("removeServicePointFromRequestPolicy:: requestPolicy: {}, servicePointId: {}",
      requestPolicy, servicePointId);

    AllowedServicePoints allowedServicePoints = requestPolicy.getAllowedServicePoints();

    removeAllowedServicePoint(servicePointId, requestPolicy, RequestType.HOLD,
      allowedServicePoints::getHold, allowedServicePoints::setHold);
    removeAllowedServicePoint(servicePointId, requestPolicy, RequestType.PAGE,
      allowedServicePoints::getPage, allowedServicePoints::setPage);
    removeAllowedServicePoint(servicePointId, requestPolicy, RequestType.RECALL,
      allowedServicePoints::getRecall, allowedServicePoints::setRecall);

    if ((allowedServicePoints.getHold() == null || allowedServicePoints.getHold().isEmpty())
      && (allowedServicePoints.getPage() == null || allowedServicePoints.getPage().isEmpty())
      && (allowedServicePoints.getRecall() == null || allowedServicePoints.getRecall().isEmpty())) {

      log.info("removeServicePointFromRequestPolicy:: all types have 0 service points, removing " +
        "allowedServicePoints from JSON");

      requestPolicy.setAllowedServicePoints(null);
    }
  }

  private static void removeAllowedServicePoint(String servicePointId, RequestPolicy requestPolicy,
    RequestType requestType, Supplier<Set<String>> getAllowedServicePointsSupplier,
    Consumer<Set<String>> setAllowedServicePointsConsumer) {

    log.debug("removeAllowedServicePoint:: deletedServicePointId={}, requestPolicy={}, " +
      "requestType={}", servicePointId, requestPolicy, requestType);

    Set<String> allowedServicePoints = getAllowedServicePointsSupplier.get();

    if (allowedServicePoints == null) {
      log.info("removeAllowedServicePoint:: allowed service points missing for type {}",
        requestType);
      return;
    }

    allowedServicePoints.remove(servicePointId);
    if (allowedServicePoints.isEmpty()) {
      log.info("removeAllowedServicePoint:: request policy ID={}: 0 allowed service point for {} " +
        "type, removing it from the policy", requestPolicy::getId, () -> requestType);

      setAllowedServicePointsConsumer.accept(null);
      requestPolicy.getRequestTypes().remove(requestType);
    }
  }
}