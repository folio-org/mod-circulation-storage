package org.folio.service.policy;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptySet;
import static java.util.function.Function.identity;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.client.InventoryStorageClient;
import org.folio.rest.jaxrs.model.AllowedServicePoints;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.support.ErrorCode;
import org.folio.support.exception.ValidationException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class RequestPolicyValidationService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final InventoryStorageClient inventoryStorageClient;

  public RequestPolicyValidationService(Vertx vertx, Map<String, String> okapiHeaders) {
    this.inventoryStorageClient = new InventoryStorageClient(vertx, okapiHeaders);
  }

  public Future<Void> validate(RequestPolicy requestPolicy) {
    String requestPolicyId = requestPolicy.getId();
    log.info("validate:: validating request policy {}", requestPolicyId);

    Collection<String> allowedServicePointIds = extractServicePointIds(requestPolicy);

    if (allowedServicePointIds.isEmpty()) {
      log.info("validate:: request policy {} has no explicitly allowed service points", requestPolicyId);
      return succeededFuture();
    }

    return inventoryStorageClient.getServicePoints(allowedServicePointIds)
      .compose(servicePoints -> validateServicePoints(allowedServicePointIds, servicePoints));
  }

  private static Collection<String> extractServicePointIds(RequestPolicy requestPolicy) {
    AllowedServicePoints servicePoints = requestPolicy.getAllowedServicePoints();

    if (servicePoints == null) {
      log.info("extractServicePointIds:: request policy has no allowed service points configured");
      return emptySet();
    }

    return Stream.of(servicePoints.getPage(), servicePoints.getHold(), servicePoints.getRecall())
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());
  }

  private static Future<Void> validateServicePoints(Collection<String> allowedServicePointIds,
    Collection<Servicepoint> servicePoints) {

    Map<String, Servicepoint> servicePointsById = servicePoints.stream()
      .collect(Collectors.toMap(Servicepoint::getId, identity()));

    for (String id : allowedServicePointIds) {
      Servicepoint servicePoint = servicePointsById.get(id);
      if (servicePoint == null || !TRUE.equals(servicePoint.getPickupLocation())) {
        log.warn("validateServicePoints:: validation failed, invalid allowed service point: {}", id);
        return failedFuture(new ValidationException(new Error()
          .withMessage("One or more Pickup locations are no longer available")
          .withCode(ErrorCode.INVALID_ALLOWED_SERVICE_POINT.name())));
      }
    }

    log.info("validateServicePoints:: request policy is valid");
    return succeededFuture();
  }

}
