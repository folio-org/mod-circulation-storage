package org.folio.service.event.handler.processor;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.folio.service.event.InventoryEventType.INVENTORY_ITEM_UPDATED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.vertx.core.Future;
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

import io.vertx.core.json.JsonObject;

public class ItemUpdateProcessorForRequest extends UpdateEventProcessor<Request> {

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
      changes.add(new Change<>(request -> request.getSearchIndex()
        .setCallNumberComponents(newCallNumberComponents.mapTo(CallNumberComponents.class))));
    }

    // compare shelving order
    String oldShelvingOrder = oldObject.getString(EFFECTIVE_SHELVING_ORDER_KEY);
    String newShelvingOrder = newObject.getString(EFFECTIVE_SHELVING_ORDER_KEY);
    if (notEqual(oldShelvingOrder, newShelvingOrder)) {
      changes.add(new Change<>(request -> request.getSearchIndex().setShelvingOrder(newShelvingOrder)));
    }

    Future<Map<String, String>> fetchLocationAndServicePoint = updateItemAndServicePoint(newObject, changes);
    return fetchLocationAndServicePoint
            .compose(locationAndSpData -> addLocationAndServicePointChanges(locationAndSpData, changes))
            .compose(res -> Future.succeededFuture(changes));
  }

  private static Future<List<Change<Request>>> addLocationAndServicePointChanges(Map<String, String> locationAndSpData, List<Change<Request>> changes) {
    changes.add(new Change<>(request -> {
      if (request.getItem() == null) {
        request.setItem(new Item());
      }
      request.getItem().setItemEffectiveLocationId(locationAndSpData.get("itemEffectiveLocationId"));
      request.getItem().setItemEffectiveLocationName(locationAndSpData.get("itemEffectiveLocationName"));
      request.getItem().setRetrievalServicePointId(locationAndSpData.get("retrievalServicePointId"));
      request.getItem().setRetrievalServicePointName(locationAndSpData.get("retrievalServicePointName"));
    }));
    return Future.succeededFuture(changes);
  }

  private Future<Map<String, String>> updateItemAndServicePoint(JsonObject newObject, List<Change<Request>> changes) {
    String effectiveLocationId = newObject.getString("effectiveLocationId");
    Map<String, String> locationAndSpData = new HashMap<>();
    locationAndSpData.put(ITEM_EFFECTIVE_LOCATION_ID, effectiveLocationId);
    return inventoryStorageClient.getLocations(Collections.singletonList(effectiveLocationId))
            .compose(locations -> setEffectiveLocationData(locations, effectiveLocationId, locationAndSpData))
            .compose(primaryServicePoint -> setRetrievalServicePointData(primaryServicePoint, locationAndSpData))
            .compose(e -> Future.succeededFuture(locationAndSpData))
            .onFailure(throwable -> {
              log.info("ItemUpdateProcessorForRequest :: Error while fetching Locations: {}", throwable.toString());
            });
  }

  private static Future<String> setEffectiveLocationData(Collection<Location> locations, String effectiveLocationId,
                                                         Map<String, String> locationAndSpData) {
    Location effectiveLocation = locations.stream()
            .filter(l -> l.getId().equals(effectiveLocationId))
            .findFirst().orElse(null);
    log.info("ItemUpdateProcessorForRequest :: setEffectiveLocationName(): locationsName: {}",
            JsonObject.mapFrom(effectiveLocation).encode());
    if (Objects.nonNull(effectiveLocation)) {
      locationAndSpData.put(ITEM_EFFECTIVE_LOCATION_NAME, effectiveLocation.getName());
      return succeededFuture(effectiveLocation.getPrimaryServicePoint().toString());
    }
    return succeededFuture();
  }

  private Future<Object> setRetrievalServicePointData(String primaryServicePoint, Map<String, String> locationAndSpData) {
    if (!StringUtils.isBlank(primaryServicePoint)) {
      locationAndSpData.put(RETRIEVAL_SERVICE_POINT_ID, primaryServicePoint);
      return inventoryStorageClient.getServicePoints(Collections.singletonList(primaryServicePoint))
              .compose(servicePoints -> {
                Servicepoint retrievalServicePoint = servicePoints.stream()
                        .filter(sp -> sp.getId().equals(primaryServicePoint))
                        .findFirst().orElse(null);
                log.info("ItemUpdateProcessorForRequest :: setServicePoint(): {}",
                        JsonObject.mapFrom(retrievalServicePoint).encode());
                if (Objects.nonNull(retrievalServicePoint)) {
                  locationAndSpData.put(RETRIEVAL_SERVICE_POINT_NAME, retrievalServicePoint.getName());
                }
                return succeededFuture();
              }).onFailure(throwable -> {
                log.info("ItemUpdateProcessorForRequest :: Error while fetching ServicePoint: {}",
                        throwable.toString());
              });
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
      // extract only properties used in request search index
      callNumberComponents
        .put(CALL_NUMBER_KEY, itemCallNumberComponents.getString(CALL_NUMBER_KEY))
        .put(CALL_NUMBER_PREFIX_KEY, itemCallNumberComponents.getString(CALL_NUMBER_PREFIX_KEY))
        .put(CALL_NUMBER_SUFFIX_KEY, itemCallNumberComponents.getString(CALL_NUMBER_SUFFIX_KEY));
    }

    return callNumberComponents;
  }

}
