package org.folio.service.migration;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDurationHMS;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

abstract class AbstractRequestMigrationService<T extends RequestMigrationContext> {
  public static final Logger log = LogManager.getLogger(AbstractRequestMigrationService.class);

  // safe number of UUIDs which fits into Okapi's URL length limit (4096 characters)
  public static final int BATCH_SIZE = 80;

  public final TenantAttributes attributes;
  public final OkapiClient okapiClient;
  public final PostgresClient postgresClient;
  public final String schemaName;
  public final List<String> errorMessages;
  public final String tableName;
  public final String moduleVersion;
  private final String migrationName;

  protected AbstractRequestMigrationService(TenantAttributes attributes, Context context,
    Map<String, String> okapiHeaders, String tableName, String moduleVersion,
    String migrationName) {

    this.attributes = attributes;
    okapiClient = new OkapiClient(context.owner(), okapiHeaders);
    postgresClient = PgUtil.postgresClient(context, okapiHeaders);
    schemaName = convertToPsqlStandard(tenantId(okapiHeaders));
    errorMessages = new ArrayList<>();
    this.tableName = tableName;
    this.moduleVersion = moduleVersion;
    this.migrationName = migrationName;
  }

  public Future<Void> migrate() {
    log.debug("migrate:: {}", migrationName);
    final long startTime = currentTimeMillis();

    if (!shouldMigrate(moduleVersion)) {
      log.info("migrate:: skipping {}. Migration version is {}", migrationName, moduleVersion);
      return succeededFuture();
    }

    log.info("migrate:: {} started, batch size is {}", migrationName, BATCH_SIZE);

    return getBatchCount()
      .compose(this::migrateRequests)
      .onSuccess(r -> log.info("migrate:: {} finished successfully", migrationName))
      .onFailure(r -> log.error("migrate:: {} failed, rolling back the changes: {}", migrationName,
        errorMessages))
      .onComplete(r -> logDuration(startTime));
  }

  public boolean shouldMigrate(String moduleVersion) {
    log.debug("shouldMigrate:: {}, moduleVersion: {}", migrationName, moduleVersion);

    if (attributes.getModuleFrom() != null && attributes.getModuleTo() != null) {
      log.info("shouldMigrate:: all attributes for {} are present: moduleFrom={}, moduleTo={}",
        migrationName, attributes.getModuleFrom(), attributes.getModuleTo());

      SemVer migrationModuleVersion = moduleVersionToSemVer(moduleVersion);
      SemVer moduleFromVersion = moduleVersionToSemVer(attributes.getModuleFrom());
      SemVer moduleToVersion = moduleVersionToSemVer(attributes.getModuleTo());

      if (moduleToVersion.compareTo(migrationModuleVersion) < 0) {
        log.info("shouldMigrate:: skipping {} for module version {}: should be {} or higher",
          migrationName, moduleToVersion, migrationModuleVersion);
        return false;
      }

      if (moduleFromVersion.compareTo(migrationModuleVersion) >= 0) {
        log.info("shouldMigrate:: skipping {} for module version {}: previous version {} " +
          "is already migrated", migrationName, moduleToVersion, moduleFromVersion);
        return false;
      }
    }
    else {
      log.info("shouldMigrate:: skipping {} - can not determine current moduleFrom or " +
        "moduleTo version", migrationName);
      return false;
    }

    log.debug("shouldMigrate:: {} for version {} will not be skipped", migrationName,
      moduleVersion);
    return true;
  }

  public Future<Integer> getBatchCount() {
    return selectRead(format("SELECT COUNT(*) FROM %s.%s", schemaName, tableName))
      .compose(this::getBatchCount);
  }

  private Future<RowSet<Row>> selectRead(String sql) {
    return Future.future(promise -> postgresClient.selectRead(sql, 0, promise));
  }

  public Future<Void> migrateRequests(int batchCount) {
    return postgresClient.withReadTrans(conn ->
      chainFutures(buildBatches(batchCount, conn), this::processBatch)
        .compose(r -> failIfErrorsOccurred())
    );
  }

  public Future<Void> updateRequests(Batch<T> batch) {
    log.debug("updateRequests:: {}, batch: {}", migrationName, batch);

    if (!errorMessages.isEmpty()) {
      log.info("updateRequests:: {}, {} update aborted - errors in previous batch(es) occurred",
        migrationName, batch);
      return succeededFuture();
    }

    List<JsonObject> migratedRequests = batch.getRequestMigrationContexts()
      .stream()
      .map(T::getNewRequest)
      .collect(toList());

    return batch.getConnection()
      .updateBatch(tableName, new JsonArray(migratedRequests))
      .onSuccess(r -> log.info("updateRequests:: {}, all requests from {} were successfully " +
        "updated", migrationName, batch))
      .mapEmpty();
  }

  public void buildNewRequests(Batch<T> batch) {
    batch.getRequestMigrationContexts()
      .forEach(this::buildNewRequest);
  }

  abstract void buildNewRequest(T context);

  public Future<Batch<T>> fetchRequests(Batch<T> batch) {
    log.debug("fetchRequests:: {}, batch: {}", migrationName, batch);

    return selectRead(format("SELECT jsonb FROM %s.%s ORDER BY id LIMIT %d OFFSET %d",
        schemaName, tableName, BATCH_SIZE, batch.getBatchNumber() * BATCH_SIZE))
      .onSuccess(r -> log.info("fetchRequests:: {}, {} {} requests fetched", migrationName, batch,
        r.size()))
      .map(this::rowSetToRequestContexts)
      .onSuccess(batch::setRequestMigrationContexts)
      .map(batch);
  }

  private List<T> rowSetToRequestContexts(RowSet<Row> rowSet) {
    return StreamSupport.stream(rowSet.spliterator(), false)
      .map(row -> row.getJsonObject("jsonb"))
      .map(this::buildContext)
      .collect(toList());
  }

  abstract T buildContext(JsonObject request);

  public Future<Batch<T>> validateRequests(Batch<T> batch) {
    log.debug("validateRequests:: {}, batch: {}", migrationName, batch);

    List<String> errors = batch.getRequestMigrationContexts()
      .stream()
      .map(this::validateRequest)
      .flatMap(Collection::stream)
      .collect(toList());

    return errors.isEmpty()
      ? succeededFuture(batch)
      : failedFuture(join(lineSeparator(), errors));
  }

  abstract Future<Void> processBatch(Batch<T> batch);

  Collection<String> validateRequest(T context) {
    return List.of();
  }

  public void logDuration(long startTime) {
    String duration = formatDurationHMS(currentTimeMillis() - startTime);
    log.info("logDuration:: {} finished in {}", migrationName, duration);
  }

  public static <T> Future<Void> chainFutures(Collection<T> list, Function<T, Future<Void>> method) {
    return list.stream().reduce(succeededFuture(),
      (acc, item) -> acc.compose(v -> method.apply(item)),
      (a, b) -> succeededFuture());
  }

  public Future<Void> handleError(Batch<T> batch, Throwable throwable) {
    log.error("handleError:: {}, {} processing failed", migrationName, batch, throwable);
    errorMessages.add(throwable.getMessage());

    return succeededFuture();
  }

  private Future<Void> failIfErrorsOccurred() {
    return errorMessages.isEmpty()
      ? succeededFuture()
      : failedFuture(join(", ", errorMessages));
  }

  private Collection<Batch<T>> buildBatches(int numberOfBatches, Conn connection) {
    log.debug("buildBatches:: {}, numberOfBatches: {}", migrationName, numberOfBatches);

    return range(0, numberOfBatches)
      .boxed()
      .map(num -> new Batch<T>(num, connection))
      .collect(toList());
  }

  private Future<Integer> getBatchCount(RowSet<Row> result) {
    log.debug("getBatchCount:: {}, result.size: {}", migrationName, result.size());

    if (!result.iterator().hasNext()) {
      return failedFuture("failed to get total number of requests");
    }

    Integer requestsCount = result.iterator().next().get(Integer.class, 0);
    int batchesCount = requestsCount / BATCH_SIZE + (requestsCount % BATCH_SIZE == 0 ? 0 : 1);
    log.info("getBatchCount:: found {} requests ({} batches)", requestsCount, batchesCount);

    return succeededFuture(batchesCount);
  }

  private static SemVer moduleVersionToSemVer(String version) {
    try {
      return new SemVer(version);
    } catch (IllegalArgumentException ex) {
      return new ModuleId(version).getSemVer();
    }
  }
}
