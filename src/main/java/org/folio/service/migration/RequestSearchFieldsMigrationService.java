package org.folio.service.migration;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.support.JsonPropertyWriter.write;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.TenantAttributes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;

public class RequestSearchFieldsMigrationService
  extends AbstractRequestMigrationService<RequestSearchMigrationContext>{

  private static final Logger log = LogManager.getLogger(RequestSearchFieldsMigrationService.class);

  private static final String TLR_MIGRATION_MODULE_VERSION = "mod-circulation-storage-16.1.0";
  private static final String ITEMS_STORAGE_URL = "/item-storage/items";
  private static final String SERVICE_POINT_URL = "/service-points";
  private static final String REQUEST_TABLE = "request";

  public RequestSearchFieldsMigrationService(TenantAttributes attributes, Context context,
    Map<String, String> okapiHeaders) {

    super(attributes, context, okapiHeaders, REQUEST_TABLE, TLR_MIGRATION_MODULE_VERSION);
  }

  public Future<Void> processBatch(Batch<RequestSearchMigrationContext> batch) {
    log.info("processBatch:: {} processing started", batch);

    return succeededFuture(batch)
      .compose(this::fetchRequests)
      .compose(this::findServicePointNames)
      .compose(this::findCallNumbers)
      .onSuccess(this::buildNewRequests)
      .compose(this::updateRequests)
      .onSuccess(r -> log.info("{} processing finished successfully", batch))
      .recover(t -> handleError(batch, t));
  }

  @Override
  RequestSearchMigrationContext buildContext(JsonObject request) {
    return new RequestSearchMigrationContext(request);
  }

  private Future<Batch<RequestSearchMigrationContext>> findServicePointNames(
    Batch<RequestSearchMigrationContext> batch) {

    Set<String> servicePointIds = batch.getRequestMigrationContexts()
      .stream()
      .map(RequestSearchMigrationContext::getPickupServicePointId)
      .filter(Objects::nonNull)
      .collect(toSet());

    if (servicePointIds.isEmpty()) {
      return succeededFuture(batch);
    }

    return okapiClient.get(SERVICE_POINT_URL, servicePointIds, "servicepoints", ServicePoint.class)
      .onSuccess(servicePoints -> saveServicePointNames(batch, servicePoints))
      .map(batch);
  }

  private static void saveServicePointNames(Batch<RequestSearchMigrationContext> batch,
    Collection<ServicePoint> servicePoints) {

    Map<String, String> servicePointIdToName = servicePoints.stream()
      .collect(toMap(ServicePoint::getId, ServicePoint::getName));

    batch.getRequestMigrationContexts().forEach(ctx ->
      ctx.setPickupServicePointName(servicePointIdToName.get(ctx.getPickupServicePointId())));
  }

  private Future<Batch<RequestSearchMigrationContext>> findCallNumbers(
    Batch<RequestSearchMigrationContext> batch) {

    Set<String> itemIds = batch.getRequestMigrationContexts()
      .stream()
      .map(RequestSearchMigrationContext::getItemId)
      .filter(Objects::nonNull)
      .collect(toSet());

    if (itemIds.isEmpty()) {
      return succeededFuture(batch);
    }

    return okapiClient.get(ITEMS_STORAGE_URL, itemIds, "items", Item.class)
      .onSuccess(items -> saveCallNumbers(batch, items))
      .map(batch);
  }

  private static void saveCallNumbers(Batch<RequestSearchMigrationContext> batch,
    Collection<Item> items) {

    Map<String, JsonObject> itemIdToCallNumberComponents = items.stream()
      .collect(toMap(Item::getId, Item::getEffectiveCallNumberComponents));

    Map<String, String> itemIdToShelvingOrder = items.stream()
      .collect(toMap(Item::getId, Item::getEffectiveShelvingOrder));

    batch.getRequestMigrationContexts().forEach(ctx -> {
      ctx.setCallNumberComponents(itemIdToCallNumberComponents.get(ctx.getItemId()));
      ctx.setShelvingOrder(itemIdToShelvingOrder.get(ctx.getItemId()));
    });
  }

  public void buildNewRequest(RequestSearchMigrationContext context) {
    final JsonObject migratedRequest = context.getOldRequest().copy();

    write(migratedRequest, "pickupServicePointName", context.getPickupServicePointName());
    write(migratedRequest, "callNumberComponents", context.getCallNumberComponents());
    write(migratedRequest, "shelvingOrder", context.getShelvingOrder());

    context.setNewRequest(migratedRequest);
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ServicePoint {
    private String id;
    private String name;
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item {
    private String id;
    private JsonObject effectiveCallNumberComponents;
    private String effectiveShelvingOrder;
  }

}
