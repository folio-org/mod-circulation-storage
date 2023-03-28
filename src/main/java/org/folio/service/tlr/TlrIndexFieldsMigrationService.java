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
 * Data migration to populate service point name and effective call number fields in requests
 */
public class TlrIndexFieldsMigrationService {
  private static final Logger log = LogManager.getLogger(TlrIndexFieldsMigrationService.class);
  
  // safe number of UUIDs which fits into Okapi's URL length limit (4096 characters)
  private static final int BATCH_SIZE = 80;

  private static final String TLR_MIGRATION_MODULE_VERSION = "mod-circulation-storage-16.1.0";
  private static final String REQUEST_TABLE = "request";
  private static final String ITEMS_STORAGE_URL = "/item-storage/items";
  private static final String SERVICE_POINTS_STORAGE_URL = "/service-points";
  private static final String HOLDINGS_STORAGE_URL="/holdings-storage/holdings";


  private static final String ITEM_REQUEST_LEVEL = "Item";
  private static final String SERVICE_POINT_ID = "pickupServicePointId";
  private static final String ID_KEY = "id";
  private static final String INSTANCE_ID_KEY = "instanceId";
  private static final String HOLDINGS_RECORD_ID_KEY = "holdingsRecordId";
  private static final String ITEM_EFFECTIVE_CALLNUMBER_KEY = "effectiveCallNumberComponents";
  private static final String ITEM_CALLNUMBER_KEY = "callNumber";
  private static final String ITEM_ID_KEY = "itemId";
  private static final String SERVICE_POINT_NAME_KEY = "name";

  private final TenantAttributes attributes;
  private final OkapiClient okapiClient;
  private final PostgresClient postgresClient;
  private final String schemaName;
  private final List<String> errorMessages;

  public TlrIndexFieldsMigrationService(TenantAttributes attributes, Context context,
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

  private static SemVer moduleVersionToSemVer(String version) {
    try {
      return new SemVer(version);
    } catch (IllegalArgumentException ex) {
      return new ModuleId(version).getSemVer();
    }
  }

  private Future<Integer> getBatchCount() {
    return postgresClient.select(format("SELECT COUNT(*) FROM %s.%s", schemaName, REQUEST_TABLE))
      .compose(this::getBatchCount);
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
    return succeededFuture();

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

  public static <T> Future<Void> chainFutures(Collection<T> list, Function<T, Future<Void>> method) {
    return list.stream().reduce(succeededFuture(),
      (acc, item) -> acc.compose(v -> method.apply(item)),
      (a, b) -> succeededFuture());
  }

  private Future<Void> handleError(Batch batch, Throwable throwable) {
    log.error("{} processing failed: {}", batch, throwable.getMessage());
    errorMessages.add(throwable.getMessage());

    return succeededFuture();
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
    private final String pickupServicePointId;
    private String pickupServicePointName;

    public static RequestMigrationContext from(JsonObject request) {
      return new RequestMigrationContext(request,
        request.getString(ID_KEY), request.getString(ITEM_ID_KEY), request.getString(SERVICE_POINT_ID));
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