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
import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;

public class RequestSearchFieldsMigrationService
  extends AbstractRequestMigrationService<RequestSearchMigrationContext>{

  private static final Logger log = LogManager.getLogger(RequestSearchFieldsMigrationService.class);

  private static final String MIGRATION_MODULE_VERSION = "mod-circulation-storage-16.1.0";
  private static final String ITEMS_STORAGE_URL = "/item-storage/items";
  private static final String SERVICE_POINT_URL = "/service-points";
  private static final String REQUEST_TABLE = "request";

  public RequestSearchFieldsMigrationService(TenantAttributes attributes, Context context,
    Map<String, String> okapiHeaders) {

    super(attributes, context, okapiHeaders, REQUEST_TABLE, MIGRATION_MODULE_VERSION);
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

    log.info("findServicePointNames:: batch={}", batch);

    Set<String> servicePointIds = batch.getRequestMigrationContexts()
      .stream()
      .map(RequestSearchMigrationContext::getPickupServicePointId)
      .filter(Objects::nonNull)
      .collect(toSet());

    if (servicePointIds.isEmpty()) {
      log.info("findServicePointNames:: 0 service points found for batch {}", batch);
      return succeededFuture(batch);
    }

    return okapiClient.get(SERVICE_POINT_URL, servicePointIds, "servicepoints", ServicePoint.class)
      .onSuccess(servicePoints -> saveServicePointNames(batch, servicePoints))
      .map(batch);
  }

  private static void saveServicePointNames(Batch<RequestSearchMigrationContext> batch,
    Collection<ServicePoint> servicePoints) {

    log.info("saveServicePointNames:: batch={}, servicePoints=Collection({} elements)", batch,
      servicePoints.size());

    Map<String, String> servicePointIdToName = servicePoints.stream()
      .collect(toMap(ServicePoint::getId, ServicePoint::getName, (a, b) -> a));

    batch.getRequestMigrationContexts().forEach(ctx ->
      ctx.setPickupServicePointName(servicePointIdToName.get(ctx.getPickupServicePointId())));
  }

  private Future<Batch<RequestSearchMigrationContext>> findCallNumbers(
    Batch<RequestSearchMigrationContext> batch) {

    log.info("findCallNumbers:: batch={}", batch);

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

    log.info("saveCallNumbers:: batch={}, items=Collection({} elements)", batch, items.size());

    Map<String, CallNumberComponents> itemIdToCallNumberComponents = items.stream()
      .collect(toMap(Item::getId, Item::getEffectiveCallNumberComponents, (a, b) -> a));

    Map<String, String> itemIdToShelvingOrder = items.stream()
      .collect(toMap(Item::getId, Item::getEffectiveShelvingOrder, (a, b) -> a));

    batch.getRequestMigrationContexts().forEach(ctx -> {
      ctx.setCallNumberComponents(itemIdToCallNumberComponents.get(ctx.getItemId()));
      ctx.setShelvingOrder(itemIdToShelvingOrder.get(ctx.getItemId()));
    });
  }

  public void buildNewRequest(RequestSearchMigrationContext context) {
    log.info("buildNewRequest:: context={}", context);

    final JsonObject migratedRequest = context.getOldRequest().copy();
    JsonObject searchIndex = new JsonObject();
    CallNumberComponents callNumberComponents = context.getCallNumberComponents();
    if (callNumberComponents != null) {
      JsonObject callNumberComponentsJsonObject = new JsonObject();
      write(callNumberComponentsJsonObject, "callNumber", callNumberComponents.callNumber);
      write(callNumberComponentsJsonObject, "prefix", callNumberComponents.prefix);
      write(callNumberComponentsJsonObject, "suffix", callNumberComponents.suffix);
      write(searchIndex, "callNumberComponents", callNumberComponentsJsonObject);
    }
    write(searchIndex, "shelvingOrder", context.getShelvingOrder());
    write(searchIndex, "pickupServicePointName", context.getPickupServicePointName());
    write(migratedRequest, "searchIndex", searchIndex);

    context.setNewRequest(migratedRequest);
  }

  @Override
  String migrationName() {
    return "Request search fields";
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
    private CallNumberComponents effectiveCallNumberComponents;
    @JsonProperty(required = false)
    private String effectiveShelvingOrder;
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CallNumberComponents {
    private String callNumber;
    private String prefix;
    private String suffix;
  }
}
