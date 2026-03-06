package org.folio.service.event.handler.processor;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.folio.service.event.InventoryEventType.INVENTORY_ITEM_UPDATED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestRepository;
import org.folio.rest.client.InventoryStorageClient;
import org.folio.rest.jaxrs.model.CallNumberComponents;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ItemUpdateProcessorForRequest extends AbstractRequestUpdateEventProcessor {

  private static final Logger log = LogManager.getLogger(ItemUpdateProcessorForRequest.class);
  private final InventoryStorageClient inventoryStorageClient;

  private static final String EFFECTIVE_SHELVING_ORDER_KEY = "effectiveShelvingOrder";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY = "effectiveCallNumberComponents";
  private static final String CALL_NUMBER_KEY = "callNumber";
  private static final String CALL_NUMBER_PREFIX_KEY = "prefix";
  private static final String CALL_NUMBER_SUFFIX_KEY = "suffix";

  public static final String ITEM_EFFECTIVE_LOCATION_ID = "itemEffectiveLocationId";
  public static final String ITEM_EFFECTIVE_LOCATION_NAME = "itemEffectiveLocationName";
  public static final String RETRIEVAL_SERVICE_POINT_ID = "retrievalServicePointId";
  public static final String RETRIEVAL_SERVICE_POINT_NAME = "retrievalServicePointName";

  public ItemUpdateProcessorForRequest(RequestRepository repository, InventoryStorageClient inventoryStorageClient) {
    super(INVENTORY_ITEM_UPDATED, repository);
    this.inventoryStorageClient = inventoryStorageClient;
  }

  @Override
  protected Future<List<Change<Request>>> collectRelevantChanges(JsonObject payload) {
    JsonObject oldObject = payload.getJsonObject("old");
    JsonObject newObject = payload.getJsonObject("new");

    List<Change<Request>> changes = new ArrayList<>();

    // compare call number components
    JsonObject oldCallNumberComponents = extractCallNumberComponents(oldObject);
    JsonObject newCallNumberComponents = extractCallNumberComponents(newObject);
    if (notEqual(oldCallNumberComponents, newCallNumberComponents)) {
      log.info("collectRelevantChanges :: callNumberComponents changed, updating search index");
      changes.add(new Change<>(request -> request.getSearchIndex()
        .setCallNumberComponents(newCallNumberComponents.mapTo(CallNumberComponents.class))));
    }

    // compare shelving order
    String oldShelvingOrder = oldObject.getString(EFFECTIVE_SHELVING_ORDER_KEY);
    String newShelvingOrder = newObject.getString(EFFECTIVE_SHELVING_ORDER_KEY);
    if (notEqual(oldShelvingOrder, newShelvingOrder)) {
      changes.add(new Change<>(request -> request.getSearchIndex().setShelvingOrder(newShelvingOrder)));
    }

    // compare effective location
    String oldEffectiveLocationId = oldObject.getString("effectiveLocationId");
    String newEffectiveLocationId = newObject.getString("effectiveLocationId");
    if (notEqual(oldEffectiveLocationId, newEffectiveLocationId)) {
      log.info("collectRelevantChanges :: effectiveLocationId changed from {} to {}",
        oldEffectiveLocationId, newEffectiveLocationId);
      return updateItemAndServicePoint(newObject)
        .compose(locationAndSpData -> addLocationAndServicePointChanges(locationAndSpData, changes))
        .compose(r -> succeededFuture(changes))
        .recover(throwable -> succeededFuture(changes));
    }

    return succeededFuture(changes);
  }

  private static Future<List<Change<Request>>> addLocationAndServicePointChanges(Map<String, String> locationAndSpData, List<Change<Request>> changes) {
    log.info("addLocationAndServicePointChanges :: locationAndSpData: {}", locationAndSpData);
    changes.add(new Change<>(request -> {
      if (request.getItem() == null) {
        request.setItem(new Item());
      }
      request.getItem().setItemEffectiveLocationId(locationAndSpData.get(ITEM_EFFECTIVE_LOCATION_ID));
      request.getItem().setItemEffectiveLocationName(locationAndSpData.get(ITEM_EFFECTIVE_LOCATION_NAME));
      request.getItem().setRetrievalServicePointId(locationAndSpData.get(RETRIEVAL_SERVICE_POINT_ID));
      request.getItem().setRetrievalServicePointName(locationAndSpData.get(RETRIEVAL_SERVICE_POINT_NAME));
    }));
    return succeededFuture(changes);
  }

  private Future<Map<String, String>> updateItemAndServicePoint(JsonObject newObject) {
    String effectiveLocationId = newObject.getString("effectiveLocationId");
    Map<String, String> locationAndSpData = new HashMap<>();
    locationAndSpData.put(ITEM_EFFECTIVE_LOCATION_ID, effectiveLocationId);

    // Use cache for location
    Location cachedLocation = locationCache.getIfPresent(effectiveLocationId);
    Future<Collection<Location>> locationsFuture;
    if (cachedLocation != null) {
      log.info("updateItemAndServicePoint:: Location cache found for id: {}",
        effectiveLocationId);
      locationsFuture = succeededFuture(singletonList(cachedLocation));
    } else {
      log.info("updateItemAndServicePoint:: Location cache missed for id: {}",
        effectiveLocationId);
      locationsFuture = inventoryStorageClient.getLocations(singletonList(effectiveLocationId))
        .onSuccess(locations -> locations.forEach(loc -> {
          locationCache.put(loc.getId(), loc);
          log.info("updateItemAndServicePoint:: Location cached for id: {}", loc.getId());
        }));
    }

    return locationsFuture
      .compose(locations -> setEffectiveLocationData(locations, effectiveLocationId, locationAndSpData))
      .compose(primaryServicePoint -> setRetrievalServicePointData(primaryServicePoint, locationAndSpData))
      .compose(e -> succeededFuture(locationAndSpData))
      .onFailure(throwable -> log.info("ItemUpdateProcessorForRequest :: Error while fetching Locations: ", throwable));
  }

  private static Future<String> setEffectiveLocationData(Collection<Location> locations, String effectiveLocationId,
    Map<String, String> locationAndSpData) {

    Location effectiveLocation = locations.stream()
            .filter(l -> l.getId().equals(effectiveLocationId))
            .findFirst().orElse(null);
    if (Objects.nonNull(effectiveLocation)) {
      locationAndSpData.put(ITEM_EFFECTIVE_LOCATION_NAME, effectiveLocation.getName());
      return succeededFuture(effectiveLocation.getPrimaryServicePoint().toString());
    }
    return succeededFuture();
  }

  private Future<Object> setRetrievalServicePointData(String primaryServicePoint, Map<String, String> locationAndSpData) {
    if (!StringUtils.isBlank(primaryServicePoint)) {
      // Use cache for service point
      Servicepoint cachedSp = servicePointCache.getIfPresent(primaryServicePoint);
      Future<Collection<Servicepoint>> servicePointsFuture;
      if (cachedSp != null) {
        log.info("setRetrievalServicePointData:: ServicePoint cache found for id: {}",
          primaryServicePoint);
        servicePointsFuture = succeededFuture(singletonList(cachedSp));
      } else {
        log.info("setRetrievalServicePointData:: ServicePoint cache missed for id: {}",
          primaryServicePoint);
        servicePointsFuture = inventoryStorageClient.getServicePoints(singletonList(primaryServicePoint))
          .onSuccess(servicePoints -> servicePoints.forEach(sp -> {
            servicePointCache.put(sp.getId(), sp);
            log.info("setRetrievalServicePointData:: ServicePoint cached for id: {}",
              sp.getId());
          }));
      }
      return servicePointsFuture
        .compose(servicePoints -> {
          Servicepoint retrievalServicePoint = servicePoints.stream()
            .filter(sp -> sp.getId().equals(primaryServicePoint))
            .findFirst().orElse(null);
          if (Objects.nonNull(retrievalServicePoint)) {
            locationAndSpData.put(RETRIEVAL_SERVICE_POINT_ID, retrievalServicePoint.getId());
            locationAndSpData.put(RETRIEVAL_SERVICE_POINT_NAME, retrievalServicePoint.getName());
          }
          return succeededFuture();
        })
        .onFailure(throwable -> log.info("ItemUpdateProcessorForRequest :: Error while fetching ServicePoint: {}", throwable.toString()));
    }
    return succeededFuture();
  }

  @Override
  protected Criterion criterionForObjectsToBeUpdated(String oldObjectId) {
    log.info("criteriaForObjectsToBeUpdated:: oldObjectId: {}", oldObjectId);

    return new Criterion(
      new Criteria()
        .addField("'itemId'")
        .setOperation("=")
        .setVal(oldObjectId));
  }

  private static JsonObject extractCallNumberComponents(JsonObject itemJson) {
    JsonObject itemCallNumberComponents = itemJson.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY);
    JsonObject callNumberComponents = new JsonObject();

    if (itemCallNumberComponents != null) {
      callNumberComponents
        .put(CALL_NUMBER_KEY, itemCallNumberComponents.getString(CALL_NUMBER_KEY))
        .put(CALL_NUMBER_PREFIX_KEY, itemCallNumberComponents.getString(CALL_NUMBER_PREFIX_KEY))
        .put(CALL_NUMBER_SUFFIX_KEY, itemCallNumberComponents.getString(CALL_NUMBER_SUFFIX_KEY));
    }

    return callNumberComponents;
  }

}
