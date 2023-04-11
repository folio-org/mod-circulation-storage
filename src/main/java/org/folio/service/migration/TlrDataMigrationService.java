package org.folio.service.migration;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.support.JsonPropertyWriter.write;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

/**
 * Data migration from item-level requests (ILR) to title-level requests (TLR)
 */
public class TlrDataMigrationService extends AbstractRequestMigrationService<TlrMigrationContext> {
  private static final Logger log = LogManager.getLogger(TlrDataMigrationService.class);
  // valid UUID version 4 variant 1
  private static final String DEFAULT_UUID = "00000000-0000-4000-8000-000000000000";

  private static final String TLR_MIGRATION_MODULE_VERSION = "mod-circulation-storage-14.0.0";
  private static final String ITEMS_STORAGE_URL = "/item-storage/items";
  private static final String HOLDINGS_STORAGE_URL = "/holdings-storage/holdings";

  private static final String REQUEST_TABLE = "request";
  private static final String ITEM_REQUEST_LEVEL = "Item";
  private static final String REQUEST_LEVEL_KEY = "requestLevel";
  private static final String INSTANCE_ID_KEY = "instanceId";
  private static final String HOLDINGS_RECORD_ID_KEY = "holdingsRecordId";
  private static final String INSTANCE_KEY = "instance";
  private static final String ITEM_KEY = "item";
  private static final String ITEM_ID_KEY = "itemId";
  private static final String TITLE_KEY = "title";
  private static final String IDENTIFIERS_KEY = "identifiers";

  public TlrDataMigrationService(TenantAttributes attributes, Context context,
    Map<String, String> okapiHeaders) {
      super(attributes, context, okapiHeaders, REQUEST_TABLE, TLR_MIGRATION_MODULE_VERSION);
    }

  public Future<Void> processBatch(Batch<TlrMigrationContext> batch) {
    log.info("{} processing started", batch);

    return succeededFuture(batch)
      .compose(this::fetchRequests)
      .compose(this::validateRequests)
      .compose(this::findHoldingsRecordIds)
      .compose(this::findInstanceIds)
      .onSuccess(this::buildNewRequests)
      .compose(this::updateRequests)
      .onSuccess(r -> log.info("{} processing finished successfully", batch))
      .recover(t -> handleError(batch, t));
  }

  @Override
  TlrMigrationContext buildContext(JsonObject request) {
    return new TlrMigrationContext(request);
  }

  public Collection<String> validateRequest(TlrMigrationContext context) {
    final JsonObject request = context.getOldRequest();
    final String requestId = context.getRequestId();
    final List<String> errors = new ArrayList<>();

    if (containsAny(request, List.of(REQUEST_LEVEL_KEY, INSTANCE_ID_KEY, INSTANCE_KEY))) {
      errors.add("request already contains TLR fields: " + requestId);
    }

    if (!containsAll(request, List.of(ITEM_ID_KEY))) {
      errors.add("request does not contain required ILR fields: " + requestId);
    }

    return errors;
  }

  private Future<Batch<TlrMigrationContext>> findHoldingsRecordIds(
    Batch<TlrMigrationContext> batch) {

    Set<String> itemIds = batch.getRequestMigrationContexts()
      .stream()
      .map(TlrMigrationContext::getItemId)
      .filter(Objects::nonNull)
      .collect(toSet());

    if (itemIds.isEmpty()) {
      return succeededFuture(batch);
    }

    return okapiClient.get(ITEMS_STORAGE_URL, itemIds, "items", Item.class)
      .onSuccess(items -> saveHoldingsRecordIds(batch, items))
      .map(batch);
  }

  private static void saveHoldingsRecordIds(Batch<TlrMigrationContext> batch,
    Collection<Item> items) {

    Map<String, String> itemIdToHoldingsRecordId = items.stream()
      .collect(toMap(Item::getId, Item::getHoldingsRecordId));

    batch.getRequestMigrationContexts().forEach(ctx ->
      ctx.setHoldingsRecordId(itemIdToHoldingsRecordId.get(ctx.getItemId())));
  }

  private Future<Batch<TlrMigrationContext>> findInstanceIds(Batch<TlrMigrationContext> batch) {
    Set<String> holdingsRecordIds = batch.getRequestMigrationContexts()
      .stream()
      .map(TlrMigrationContext::getHoldingsRecordId)
      .filter(Objects::nonNull)
      .collect(toSet());

    if (holdingsRecordIds.isEmpty()) {
      return succeededFuture(batch);
    }

    return okapiClient.get(HOLDINGS_STORAGE_URL, holdingsRecordIds, "holdingsRecords", HoldingsRecord.class)
      .onSuccess(holdingsRecords -> saveInstanceIds(batch, holdingsRecords))
      .map(batch);
  }

  private static void saveInstanceIds(Batch<TlrMigrationContext> batch,
    Collection<HoldingsRecord> holdingsRecords) {

    Map<String, String> holdingsRecordIdInstanceId = holdingsRecords.stream()
      .collect(toMap(HoldingsRecord::getId, HoldingsRecord::getInstanceId));

    batch.getRequestMigrationContexts().forEach(ctx ->
      ctx.setInstanceId(holdingsRecordIdInstanceId.get(ctx.getHoldingsRecordId())));
  }

  public void buildNewRequest(TlrMigrationContext context) {
    String holdingsRecordId = context.getHoldingsRecordId();
    if (holdingsRecordId == null) {
      holdingsRecordId = DEFAULT_UUID;
      log.warn("Failed to determine holdingsRecordId for request {}, using default value: {}",
        context.getRequestId(), holdingsRecordId);
    }

    String instanceId = context.getInstanceId();
    if (instanceId == null) {
      instanceId = DEFAULT_UUID;
      log.warn("Failed to determine instanceId for request {}, using default value: {}",
        context.getRequestId(), instanceId);
    }

    final JsonObject migratedRequest = context.getOldRequest().copy();
    JsonObject item = migratedRequest.getJsonObject(ITEM_KEY);
    JsonObject instance = new JsonObject();

    if (item != null) {
      write(instance, TITLE_KEY, item.getString(TITLE_KEY));
      write(instance, IDENTIFIERS_KEY, item.getJsonArray(IDENTIFIERS_KEY));

      item.remove(TITLE_KEY);
      item.remove(IDENTIFIERS_KEY);
    }
    else {
      log.warn("'item' field is missing from request {}, 'instance' field will not be " +
        "added", context.getRequestId());
    }

    write(migratedRequest, INSTANCE_ID_KEY, instanceId);
    write(migratedRequest, HOLDINGS_RECORD_ID_KEY, holdingsRecordId);
    write(migratedRequest, REQUEST_LEVEL_KEY, ITEM_REQUEST_LEVEL);
    write(migratedRequest, INSTANCE_KEY, instance);

    context.setNewRequest(migratedRequest);
  }

  private static boolean containsAll(JsonObject request, List<String> fieldNames) {
    return fieldNames.stream()
      .allMatch(request::containsKey);
  }

  private static boolean containsAny(JsonObject request, List<String> fieldNames) {
    return fieldNames.stream()
      .anyMatch(request::containsKey);
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item {
    private String id;
    private String holdingsRecordId;
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HoldingsRecord {
    private String id;
    private String instanceId;
  }
}
