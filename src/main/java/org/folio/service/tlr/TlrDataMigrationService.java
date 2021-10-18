package org.folio.service.tlr;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import lombok.Getter;
import lombok.Setter;

public class TlrDataMigrationService {
  private static final Logger LOG = LoggerFactory.getLogger(TlrDataMigrationService.class);

  public static final String TLR_MIGRATION_MODULE_VERSION = "mod-circulation-storage-13.2.0";
  public static final String LOG_PREFIX = "TLR data migration: ";
  public static final String REQUEST_TABLE = "request";
  public static final Integer BATCH_SIZE = 10;
  public static final String ITEMS_STORAGE_URL = "/item-storage/items";
  public static final String HOLDINGS_STORAGE_URL = "/holdings-storage/holdings";

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
      .onComplete(asyncResult -> {
        if (asyncResult.succeeded()) {
          RowSet<Row> result = asyncResult.result();
          if (result.iterator().hasNext()) {
            Row row = result.iterator().next();
            Integer count = row.get(Integer.class, 0);
            int numberOfBatches = count / BATCH_SIZE + (count % BATCH_SIZE == 0 ? 0 : 1);

            logInfo(format("found %d requests (%d batch(es))", count, numberOfBatches));

            chainFutures(range(0, numberOfBatches).boxed().collect(toList()), this::processBatch)
              .onComplete(batchesAsyncResult -> {
                if (migrationContext.isSuccessful()) {
                  updaterService.update();
                }
                else {
                  promise.fail(String.join(", ", migrationContext.getErrorMessages()));
                }
              });
          }
          else {
            logError("failed to get a total number of requests");
          }
        }
        else {
          promise.fail(asyncResult.cause());
        }
      });

    return promise.future();
  }

  private Future<Void> processBatch(int batchNumber) {
    logInfo(format("start processing batch %d", batchNumber));

    Promise<Void> promise = Promise.promise();

    postgresClient.select(format("SELECT id, jsonb FROM %s.%s ORDER BY id LIMIT %d OFFSET %d",
      schemaName, REQUEST_TABLE, BATCH_SIZE, batchNumber * BATCH_SIZE))
      .onComplete(asyncResult -> {
        RowSet<Row> result = asyncResult.result();
        if (asyncResult.succeeded()) {
          logInfo(format("found %d requests in batch %d", result.rowCount(), batchNumber));
          List<JsonObject> requestList = new ArrayList<>();
          for (Row requestRow : result) {
            JsonObject request = requestRow.getJsonObject("jsonb");
            requestList.add(request);
          }
          chainFutures(requestList, this::processRequest)
            .onComplete(batchesAsyncResult -> {
              if (batchesAsyncResult.succeeded()) {
                logInfo(format("finished processing batch %d", batchNumber));
              }
              else {
                logError(format("failed to process requests in batch %d", batchNumber));
              }
              promise.complete();
            });
        }
        else {
          logError(format("failed to select requests for batch %d", batchNumber));
          promise.complete();
        }
      });

    return promise.future();
  }

  private Future<Void> processRequest(JsonObject request) {
    logInfo(format("processing request %s", request.getString("id")));

    String requestId = request.getString("id");
    Future<Void> requestIsValid = validateRequest(request) ? succeededFuture() :
      failedFuture(format("request %s validation failed", requestId));

    Promise<Void> promise = Promise.promise();

    requestIsValid
      .compose(v -> determineInstanceId(request))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          logInfo(format("determined instanceId %s, request %s", ar.result(), requestId));
        }
        else {
          logError(format("failed to determine instanceId, request %s", requestId));
        }
        promise.complete();
      });

    return promise.future();
  }

  private Future<String> determineInstanceId(JsonObject request) {
    return okapiClient.getById(ITEMS_STORAGE_URL, request.getString("itemId"), Item.class)
      .map(Item::getHoldingsRecordId)
      .compose(id -> okapiClient.getById(HOLDINGS_STORAGE_URL, id, HoldingsRecord.class))
      .map(HoldingsRecord::getInstanceId)
      .map(instanceId -> addInstanceIdToContext(request, instanceId));
  }

  private String addInstanceIdToContext(JsonObject request, String instanceId) {
    migrationContext.getInstanceIds().put(request.getString("id"), instanceId);
    return instanceId;
  }

  private boolean validateRequest(JsonObject request) {
    boolean doesNotContainTlrFields = allOrNonePresent(request,
      List.of("requestLevel", "instanceId", "instance"), false);

    if (!doesNotContainTlrFields) {
      logInfo(format("skipping request %s - already contains TLR fields", request.getString("id")));
    }

    boolean containsAllRequiredIlrFields =
      allOrNonePresent(request, List.of("itemId", "item"), true)
        && allOrNonePresent(request.getJsonObject("item"), List.of("barcode"), true);

    if (!containsAllRequiredIlrFields) {
      logInfo(format("skipping request %s - does not contain required ILR fields",
        request.getString("id")));
    }

    boolean containsAllNonRequiredIlrFields = containsAllRequiredIlrFields &&
      allOrNonePresent(request.getJsonObject("item"),
        List.of("title", "identifiers"), true);

    if (!containsAllNonRequiredIlrFields) {
      logInfo(format("request %s does not contain some non-required ILR fields, values will be " +
          "copied from the instance record", request.getString("id")));
    }

    return doesNotContainTlrFields && containsAllRequiredIlrFields;
  }

  private boolean allOrNonePresent(JsonObject request, List<String> fieldNames, boolean present) {
    return fieldNames.stream()
      .map(fieldName -> present == request.containsKey(fieldName))
      .reduce(Boolean::logicalAnd)
      .orElse(true);
  }

  private void logDebug(String message) {
    LOG.debug(format("%s%s", LOG_PREFIX, message));
  }

  private void logInfo(String message) {
    LOG.info(format("%s%s", LOG_PREFIX, message));
  }

  private void logError(String message) {
    String fullMessage = format("%s%s", LOG_PREFIX, message);

    LOG.error(fullMessage);

    migrationContext.setSuccessful(false);
    migrationContext.getErrorMessages().add(fullMessage);
  }

  public static <T> Future<Void> chainFutures(List<T> list,
    Function<T, Future<Void>> method) {

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
//    @JsonProperty("holdingsRecordId")
    private String holdingsRecordId;
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HoldingsRecord {
//    @JsonProperty("instanceId")
    private String instanceId;
  }
}
