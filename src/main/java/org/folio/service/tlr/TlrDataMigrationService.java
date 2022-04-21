package org.folio.service.tlr;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.IntStream.range;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDurationHMS;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.support.JsonPropertyWriter.write;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.SemVer;
import org.folio.rest.client.OkapiClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Data migration from item-level requests (ILR) to title-level requests (TLR)
 */
public class TlrDataMigrationService {
  private static final Logger log = LogManager.getLogger(TlrDataMigrationService.class);

  // safe number of UUIDs which fits into Okapi's URL length limit (4096 characters)
  private static final int BATCH_SIZE = 80;

  private static final String TLR_MIGRATION_MODULE_VERSION = "mod-circulation-storage-13.2.0";
  private static final String REQUEST_TABLE = "request";
  private static final String ITEMS_STORAGE_URL = "/item-storage/items";
  private static final String HOLDINGS_STORAGE_URL = "/holdings-storage/holdings";

  private static final String ITEM_REQUEST_LEVEL = "Item";
  private static final String REQUEST_LEVEL_KEY = "requestLevel";
  private static final String ID_KEY = "id";
  private static final String INSTANCE_ID_KEY = "instanceId";
  private static final String HOLDINGS_RECORD_ID_KEY = "holdingsRecordId";
  private static final String INSTANCE_KEY = "instance";
  private static final String ITEM_KEY = "item";
  private static final String ITEM_ID_KEY = "itemId";
  private static final String TITLE_KEY = "title";
  private static final String IDENTIFIERS_KEY = "identifiers";

  private final TenantAttributes attributes;
  private final OkapiClient okapiClient;
  private final PostgresClient postgresClient;
  private final String schemaName;
  private final List<String> errorMessages;

  public TlrDataMigrationService(TenantAttributes attributes, Context context,
    Map<String, String> okapiHeaders) {

    this.attributes = attributes;
    okapiClient = new OkapiClient(context.owner(), okapiHeaders);
    postgresClient = PgUtil.postgresClient(context, okapiHeaders);
    schemaName = convertToPsqlStandard(tenantId(okapiHeaders));
    errorMessages = new ArrayList<>();
  }

  public Future<Void> migrate() {
    final long startTime = currentTimeMillis();

    if (attributes.getModuleFrom() != null && attributes.getModuleTo() != null) {
      SemVer migrationModuleVersion = moduleVersionToSemVer(TLR_MIGRATION_MODULE_VERSION);
      SemVer moduleFromVersion = moduleVersionToSemVer(attributes.getModuleFrom());
      SemVer moduleToVersion = moduleVersionToSemVer(attributes.getModuleTo());

      if (moduleToVersion.compareTo(migrationModuleVersion) < 0) {
        log.info("skipping migration for module version {}: should be {} or higher",
          moduleToVersion, migrationModuleVersion);
        return succeededFuture();
      }

      if (moduleFromVersion.compareTo(migrationModuleVersion) >= 0) {
        log.info("skipping migration for module version {}: previous version {} is already migrated",
          moduleToVersion, moduleFromVersion);
        return succeededFuture();
      }
    }
    else {
      log.info("skipping migration - can not determine current moduleFrom or moduleTo version");
      return succeededFuture();
    }

    log.info("migration started, batch size " + BATCH_SIZE);

    return getBatchCount()
      .compose(this::migrateRequests)
      .onSuccess(r -> log.info("Migration finished successfully"))
      .onFailure(r -> log.error("Migration failed, rolling back the changes: {}", errorMessages))
      .onComplete(r -> logDuration(startTime));
  }

  private Future<Integer> getBatchCount() {
    return postgresClient.select(format("SELECT COUNT(*) FROM %s.%s", schemaName, REQUEST_TABLE))
      .compose(this::getBatchCount);
  }

  private Future<Integer> getBatchCount(RowSet<Row> result) {
    if (!result.iterator().hasNext()) {
      return failedFuture("failed to get total number of requests");
    }

    Integer requestsCount = result.iterator().next().get(Integer.class, 0);
    int batchesCount = requestsCount / BATCH_SIZE + (requestsCount % BATCH_SIZE == 0 ? 0 : 1);
    log.info("found {} requests ({} batches)", requestsCount, batchesCount);

    return succeededFuture(batchesCount);
  }

  private Future<Void> migrateRequests(int batchCount) {
    return postgresClient.withTrans(conn ->
      chainFutures(buildBatches(batchCount, conn), this::processBatch)
        .compose(r -> failIfErrorsOccurred())
    );
  }

  private static Collection<Batch> buildBatches(int numberOfBatches, Conn connection) {
    return range(0, numberOfBatches)
      .boxed()
      .map(num -> new Batch(num, connection))
      .collect(toList());
  }

  private Future<Void> processBatch(Batch batch) {
    log.info("{} processing started", batch);

    return succeededFuture(batch)
      .compose(this::fetchRequests)
      .compose(this::validateRequests)
      .compose(this::findHoldingsRecordIds)
      .compose(this::findInstanceIds)
      .compose(this::failWhenNotAllInstanceIdsWereFound)
      .onSuccess(this::buildNewRequests)
      .compose(this::updateRequests)
      .onSuccess(r -> log.info("{} processing finished successfully", batch))
      .recover(t -> handleError(batch, t));
  }

  private Future<Batch> fetchRequests(Batch batch) {
    return postgresClient.select(format("SELECT jsonb FROM %s.%s ORDER BY id LIMIT %d OFFSET %d",
        schemaName, REQUEST_TABLE, BATCH_SIZE, batch.getBatchNumber() * BATCH_SIZE))
      .onSuccess(r -> log.info("{} {} requests fetched", batch, r.size()))
      .map(this::rowSetToRequestContexts)
      .onSuccess(batch::setRequestMigrationContexts)
      .map(batch);
  }

  private List<RequestMigrationContext> rowSetToRequestContexts(RowSet<Row> rowSet) {
    return StreamSupport.stream(rowSet.spliterator(), false)
      .map(row -> row.getJsonObject("jsonb"))
      .map(RequestMigrationContext::from)
      .collect(toList());
  }

  private Future<Batch> validateRequests(Batch batch) {
    List<String> errors = batch.getRequestMigrationContexts()
      .stream()
      .map(this::validateRequest)
      .flatMap(Collection::stream)
      .collect(toList());

    return errors.isEmpty()
      ? succeededFuture(batch)
      : failedFuture(join(lineSeparator(), errors));
  }

  private Collection<String> validateRequest(RequestMigrationContext context) {
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

  private Future<Batch> findHoldingsRecordIds(Batch batch) {
    Set<String> itemIds = batch.getRequestMigrationContexts()
      .stream()
      .map(RequestMigrationContext::getItemId)
      .filter(Objects::nonNull)
      .collect(toSet());

    if (itemIds.isEmpty()) {
      return succeededFuture(batch);
    }

    return okapiClient.get(ITEMS_STORAGE_URL, itemIds, "items", Item.class)
      .onSuccess(items -> saveHoldingsRecordIds(batch, items))
      .map(batch);
  }

  private static void saveHoldingsRecordIds(Batch batch, Collection<Item> items) {
    Map<String, String> itemIdToHoldingsRecordId = items.stream()
      .collect(toMap(Item::getId, Item::getHoldingsRecordId));

    batch.getRequestMigrationContexts().forEach(ctx ->
      ctx.setHoldingsRecordId(itemIdToHoldingsRecordId.get(ctx.getItemId())));
  }

  private Future<Batch> findInstanceIds(Batch batch) {
    Set<String> holdingsRecordIds = batch.getRequestMigrationContexts()
      .stream()
      .map(RequestMigrationContext::getHoldingsRecordId)
      .filter(Objects::nonNull)
      .collect(toSet());

    if (holdingsRecordIds.isEmpty()) {
      return succeededFuture(batch);
    }

    return okapiClient.get(HOLDINGS_STORAGE_URL, holdingsRecordIds, "holdingsRecords", HoldingsRecord.class)
      .onSuccess(holdingsRecords -> saveInstanceIds(batch, holdingsRecords))
      .map(batch);
  }

  private static void saveInstanceIds(Batch batch, Collection<HoldingsRecord> holdingsRecords) {
    Map<String, String> holdingsRecordIdInstanceId = holdingsRecords.stream()
      .collect(toMap(HoldingsRecord::getId, HoldingsRecord::getInstanceId));

    batch.getRequestMigrationContexts().forEach(ctx ->
      ctx.setInstanceId(holdingsRecordIdInstanceId.get(ctx.getHoldingsRecordId())));
  }

  private Future<Batch> failWhenNotAllInstanceIdsWereFound(Batch batch) {
    List<String> failedContexts = batch.getRequestMigrationContexts()
      .stream()
      .filter(ctx -> ctx.getInstanceId() == null)
      .map(RequestMigrationContext::toString)
      .collect(toList());

    return failedContexts.isEmpty()
      ? succeededFuture(batch)
      : failedFuture("failed to find instance IDs: " + join(lineSeparator(), failedContexts));
  }

  private void buildNewRequests(Batch batch) {
    batch.getRequestMigrationContexts()
      .forEach(this::buildNewRequest);
  }

  private void buildNewRequest(RequestMigrationContext context) {
    final JsonObject migratedRequest = context.getOldRequest().copy();

    JsonObject item = migratedRequest.getJsonObject(ITEM_KEY);
    JsonObject instance = new JsonObject();

    write(instance, TITLE_KEY, item.getString(TITLE_KEY));
    write(instance, IDENTIFIERS_KEY, item.getJsonArray(IDENTIFIERS_KEY));

    write(migratedRequest, REQUEST_LEVEL_KEY, ITEM_REQUEST_LEVEL);
    write(migratedRequest, INSTANCE_ID_KEY, context.getInstanceId());
    write(migratedRequest, HOLDINGS_RECORD_ID_KEY, context.getHoldingsRecordId());
    write(migratedRequest, INSTANCE_KEY, instance);

    item.remove(TITLE_KEY);
    item.remove(IDENTIFIERS_KEY);

    context.setNewRequest(migratedRequest);
  }

  private Future<Void> updateRequests(Batch batch) {
    if (!errorMessages.isEmpty()) {
      log.info("{} batch update aborted - errors in previous batch(es) occurred", batch);
      return succeededFuture();
    }

    List<JsonObject> migratedRequests = batch.getRequestMigrationContexts()
      .stream()
      .map(RequestMigrationContext::getNewRequest)
      .collect(toList());

    return batch.getConnection()
      .updateBatch(REQUEST_TABLE, new JsonArray(migratedRequests))
      .onSuccess(r -> log.info("{} all requests were successfully updated", batch))
      .mapEmpty();
  }

  private Future<Void> failIfErrorsOccurred() {
    return errorMessages.isEmpty()
      ? succeededFuture()
      : failedFuture(join(", ", errorMessages));
  }

  private static void logDuration(long startTime) {
    String duration = formatDurationHMS(currentTimeMillis() - startTime);
    log.info("Migration finished in {}", duration);
  }

  private static boolean containsAll(JsonObject request, List<String> fieldNames) {
    return fieldNames.stream()
      .allMatch(request::containsKey);
  }

  private static boolean containsAny(JsonObject request, List<String> fieldNames) {
    return fieldNames.stream()
      .anyMatch(request::containsKey);
  }

  private Future<Void> handleError(Batch batch, Throwable throwable) {
    log.error("{} processing failed: {}", batch, throwable.getMessage());
    errorMessages.add(throwable.getMessage());

    return succeededFuture();
  }

  public static <T> Future<Void> chainFutures(Collection<T> list, Function<T, Future<Void>> method) {
    return list.stream().reduce(succeededFuture(),
      (acc, item) -> acc.compose(v -> method.apply(item)),
      (a, b) -> succeededFuture());
  }

  private static SemVer moduleVersionToSemVer(String version) {
    try {
      return new SemVer(version);
    } catch (IllegalArgumentException ex) {
      return new ModuleId(version).getSemVer();
    }
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

  @Getter
  @Setter
  @RequiredArgsConstructor
  public static class RequestMigrationContext {
    private final JsonObject oldRequest;
    private JsonObject newRequest;
    private final String requestId;
    private final String itemId;
    private String holdingsRecordId;
    private String instanceId;

    public static RequestMigrationContext from(JsonObject request) {
      return new RequestMigrationContext(request,
        request.getString(ID_KEY), request.getString(ITEM_ID_KEY));
    }

    @Override
    public String toString() {
      return "RequestMigrationContext{" +
        "requestId='" + requestId + '\'' +
        ", itemId='" + itemId + '\'' +
        ", holdingsRecordId='" + holdingsRecordId + '\'' +
        ", instanceId='" + instanceId + '\'' +
        '}';
    }
  }

  @Getter
  @Setter
  @RequiredArgsConstructor
  private static class Batch {
    private final int batchNumber;
    private final Conn connection;
    private List<RequestMigrationContext> requestMigrationContexts = new ArrayList<>();

    @Override
    public String toString() {
      return String.format("[batch #%d - %d requests]", batchNumber, requestMigrationContexts.size());
    }
  }

}
