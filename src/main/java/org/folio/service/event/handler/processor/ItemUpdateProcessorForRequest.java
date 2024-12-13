package org.folio.service.event.handler.processor;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.folio.service.event.InventoryEventType.INVENTORY_ITEM_UPDATED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.Handler;
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
  private static final String EFFECTIVE_SHELVING_ORDER_KEY = "effectiveShelvingOrder";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS_KEY = "effectiveCallNumberComponents";
  private static final String CALL_NUMBER_KEY = "callNumber";
  private static final String CALL_NUMBER_PREFIX_KEY = "prefix";
  private static final String CALL_NUMBER_SUFFIX_KEY = "suffix";
  private final InventoryStorageClient inventoryStorageClient;

  public ItemUpdateProcessorForRequest(RequestRepository repository, InventoryStorageClient inventoryStorageClient) {
    super(INVENTORY_ITEM_UPDATED, repository);
    this.inventoryStorageClient = inventoryStorageClient;
  }

  protected List<Change<Request>> collectRelevantChanges(JsonObject payload) {
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

    updateItemAndServicePoint(newObject, changes);


    return changes;
  }

  private void updateItemAndServicePoint(JsonObject newObject,
                                         List<Change<Request>> changes) {
    String effectiveLocationId = newObject.getString("effectiveLocationId");
    log.info("ItemUpdateProcessorForRequest :: updateItemAndServicePoint(): " +
      "effectiveLocationId: {}", effectiveLocationId);
    changes.add(new Change<>(request -> {
      if (request.getItem() == null) {
        request.setItem(new Item());
      }
      request.getItem().setItemEffectiveLocationId(effectiveLocationId);
      inventoryStorageClient.getLocations(Collections.singletonList(effectiveLocationId))
        .compose(locations -> setEffectiveLocationName(request, locations,
          effectiveLocationId))
        .compose(primaryServicePoint -> {
          if (!StringUtils.isBlank(primaryServicePoint)) {
            request.getItem().setRetrievalServicePointId(primaryServicePoint);
            inventoryStorageClient.getServicePoints(Collections.singletonList(primaryServicePoint))
              .compose(servicePoints -> setServicePoint(request, servicePoints,
                primaryServicePoint)).onFailure(throwable -> {
                  log.info("ItemUpdateProcessorForRequest :: Error while " +
                    "fetching ServicePoint: {}", throwable.toString());
              });;
          }
          return succeededFuture();
        }).onFailure(throwable -> {
          log.info("ItemUpdateProcessorForRequest :: Error while fetching " +
            "gLocations: {}", throwable.toString());
        });
    }));
  }

  private Future<String> setEffectiveLocationName(Request request,
                                                Collection<Location> locations, String effectiveLocationId) {
    Location effectiveLocation = locations.stream()
      .filter(l -> l.getId().equals(effectiveLocationId))
      .findFirst().orElse(null);
    log.info("ItemUpdateProcessorForRequest :: setEffectiveLocationName(): " +
      "locationsName: {}", effectiveLocation.getName());
    if(Objects.nonNull(effectiveLocation)) {
      request.getItem().setItemEffectiveLocationName(effectiveLocation.getName());
      return succeededFuture(effectiveLocation.getPrimaryServicePoint().toString());
    }
    return succeededFuture();
  }

  private Future<Void> setServicePoint(Request request,
                                       Collection<Servicepoint> servicePoints, String primaryServicePoint) {
    Servicepoint retrievalServicePoint = servicePoints.stream()
      .filter(sp -> sp.getId().equals(primaryServicePoint))
      .findFirst().orElse(null);
    log.info("ItemUpdateProcessorForRequest :: setServicePoint(): " +
      "servicePointName: {}", retrievalServicePoint.getName());
    if(Objects.nonNull(retrievalServicePoint)) {
      request.getItem().setRetrievalServicePointName(retrievalServicePoint.getName());
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
