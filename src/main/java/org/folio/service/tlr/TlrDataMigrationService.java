package org.folio.service.tlr;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.util.UuidUtil.isUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.SemVer;
import org.folio.rest.client.OkapiClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;

/**
 * Data migration from item-level requests (ILR) to title-level requests (TLR)
 */
public class TlrDataMigrationService {
  private static final Logger LOG = LoggerFactory.getLogger(TlrDataMigrationService.class);

  private static final String TLR_MIGRATION_MODULE_VERSION = "mod-circulation-storage-13.2.0";
  private static final String LOG_PREFIX = "TLR data migration: ";
  private static final String REQUEST_TABLE = "request";
  private static final Integer BATCH_SIZE = 1000;
  private static final String ITEMS_STORAGE_URL = "/item-storage/items";
  private static final String HOLDINGS_STORAGE_URL = "/holdings-storage/holdings";

  private final TenantAttributes attributes;
  private final OkapiClient okapiClient;
  private final PostgresClient postgresClient;
  private final String schemaName;
  private final TlrDataMigrationContext migrationContext;
  private final TlrDataMigrationUpdaterService updaterService;

  public TlrDataMigrationService(TenantAttributes attributes, Context context,
    Map<String, String> okapiHeaders) {

    this.attributes = attributes;
    okapiClient = new OkapiClient(context.owner(), okapiHeaders);
    postgresClient = PgUtil.postgresClient(context, okapiHeaders);
    schemaName = convertToPsqlStandard(tenantId(okapiHeaders));
    migrationContext = new TlrDataMigrationContext();
    updaterService = new TlrDataMigrationUpdaterService(migrationContext);
  }

  public Future<Void> migrate() {
    if (attributes.getModuleFrom() != null && attributes.getModuleTo() != null) {
      SemVer migrationModuleVersion = moduleVersionToSemVer(TLR_MIGRATION_MODULE_VERSION);
      SemVer moduleFromVersion = moduleVersionToSemVer(attributes.getModuleFrom());
      SemVer moduleToVersion = moduleVersionToSemVer(attributes.getModuleTo());

      if (moduleToVersion.compareTo(migrationModuleVersion) < 0) {
        logInfo(format("skipping migration for module version %s: should be %s or higher",
          moduleToVersion, migrationModuleVersion));
        return succeededFuture();
      }
      else if (moduleFromVersion.compareTo(migrationModuleVersion) >= 0) {
        logInfo(format("skipping migration for module version %s: previous version %s " +
          "is already migrated", moduleToVersion, moduleFromVersion));
        return succeededFuture();
      }
    }
    else {
      logInfo("skipping migration - can not determine current moduleFrom or moduleTo version");
      return succeededFuture();
    }

    logInfo("start");

    Promise<Void> promise = Promise.promise();
    migrationContext.setMigrationProcessPromise(promise);

    postgresClient.select(format("SELECT COUNT(*) FROM %s.%s", schemaName, REQUEST_TABLE))
      .onFailure(promise::fail)
      .onSuccess(result -> {
        if (!result.iterator().hasNext()) {
          handleError("failed to get a total number of requests");
          return;
        }

        Integer count = result.iterator().next().get(Integer.class, 0);
        int numberOfBatches = count / BATCH_SIZE + (count % BATCH_SIZE == 0 ? 0 : 1);

        logInfo(format("found %d requests (%d batch(es))", count, numberOfBatches));

        chainFutures(range(0, numberOfBatches).boxed().collect(toList()), this::processBatch)
          .onComplete(batchesAsyncResult -> {
            if (migrationContext.isSuccessful()) {
              updaterService.update();
            } else {
              promise.fail(join(", ", migrationContext.getErrorMessages()));
            }
          });
      });

    return promise.future();
  }

  private Future<Void> processBatch(int batchNumber) {
    logInfo(format("start processing batch %d", batchNumber));

    Promise<Void> promise = Promise.promise();

    postgresClient.select(format("SELECT jsonb FROM %s.%s ORDER BY id LIMIT %d OFFSET %d",
      schemaName, REQUEST_TABLE, BATCH_SIZE, batchNumber * BATCH_SIZE))
      .onFailure(t -> {
        handleError(format("failed to select requests for batch %d: %s", batchNumber,
          t.getMessage()));
        promise.complete();
      })
      .onSuccess(result -> {
        logInfo(format("found %d requests in batch %d", result.rowCount(), batchNumber));

        List<JsonObject> requestList = StreamSupport.stream(result.spliterator(), false)
          .map(row -> row.getJsonObject("jsonb"))
          .collect(Collectors.toList());

        chainFutures(requestList, this::processRequest)
          .onComplete(batchesAsyncResult -> {
            if (batchesAsyncResult.succeeded()) {
              logInfo(format("finished processing batch %d", batchNumber));
            }
            else {
              handleError(format("failed to process requests in batch %d", batchNumber));
            }
            promise.complete();
          });
      });

    return promise.future();
  }

  private Future<Void> processRequest(JsonObject request) {
    String requestId = request.getString("id");

    logInfo(format("processing request %s", requestId));

    return validateRequest(request)
      .compose(this::determineInstanceId)
      .onSuccess(instanceId -> logInfo(format("determined instanceId %s, request %s",
        instanceId, requestId)))
      .onFailure(t -> handleError(format("failed to determine instanceId, request %s: %s",
        requestId, t.getMessage())))
      // In order to collect ALL errors we need to return succeeded future, but the error will be
      // saved in the context.
      .otherwiseEmpty()
      .mapEmpty();
  }

  private Future<String> determineInstanceId(JsonObject request) {
    return okapiClient.getById(ITEMS_STORAGE_URL, request.getString("itemId"), Item.class)
      .map(Item::getHoldingsRecordId)
      .compose(id -> okapiClient.getById(HOLDINGS_STORAGE_URL, id, HoldingsRecord.class))
      .map(HoldingsRecord::getInstanceId)
      .compose(instanceId -> addInstanceIdToContext(request, instanceId));
  }

  private Future<String> addInstanceIdToContext(JsonObject request, String instanceId) {
    String requestId = request.getString("id");

    if (!isUuid(instanceId)) {
      String message = format("instanceId %s is not a UUID (request %s)", instanceId, requestId);
      return failedFuture(message);
    }

    migrationContext.getInstanceIds().put(requestId, instanceId);
    return succeededFuture(instanceId);
  }

  private Future<JsonObject> validateRequest(JsonObject request) {
    String requestId = request.getString("id");
    List<String> errorList = new ArrayList<>();

    boolean doesNotContainTlrFields = containsNone(request,
      List.of("requestLevel", "instanceId", "instance"));

    if (!doesNotContainTlrFields) {
      errorList.add(format("skipping request %s - already contains TLR fields", requestId));
    }

    boolean containsAllRequiredIlrFields = containsAll(request, List.of("itemId"));

    if (!containsAllRequiredIlrFields) {
      errorList.add(format("skipping request %s - does not contain required ILR fields",
        requestId));
    }

    return (doesNotContainTlrFields && containsAllRequiredIlrFields)
      ? succeededFuture(request)
      : failedFuture(format("request %s validation failed: %s", requestId, join(", ", errorList)));
  }

  private boolean containsAll(JsonObject request, List<String> fieldNames) {
    return fieldNames.stream()
      .allMatch(request::containsKey);
  }

  private boolean containsNone(JsonObject request, List<String> fieldNames) {
    return fieldNames.stream()
      .noneMatch(request::containsKey);
  }

  private void logDebug(String message) {
    LOG.debug(format("%s%s", LOG_PREFIX, message));
  }

  private void logInfo(String message) {
    LOG.info(format("%s%s", LOG_PREFIX, message));
  }

  private void handleError(String message) {
    String fullMessage = format("%s%s", LOG_PREFIX, message);

    LOG.error(fullMessage);

    migrationContext.setSuccessful(false);
    migrationContext.getErrorMessages().add(fullMessage);
  }

  public static <T> Future<Void> chainFutures(List<T> list, Function<T, Future<Void>> method) {
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
    private String holdingsRecordId;
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HoldingsRecord {
    private String instanceId;
  }
}