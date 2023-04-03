package org.folio.service.tlr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.SemVer;
import org.folio.rest.client.OkapiClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.PgUtil;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;

public class AbstractRequestMigrationService {
  public static final Logger log = LogManager.getLogger(AbstractRequestMigrationService.class);

  // safe number of UUIDs which fits into Okapi's URL length limit (4096 characters)
  public static final int BATCH_SIZE = 80;

  private static final String REQUEST_TABLE = "request";

  public final TenantAttributes attributes;
  public final OkapiClient okapiClient;
  public final PostgresClient postgresClient;
  public final String schemaName;
  public final List<String> errorMessages;

  public AbstractRequestMigrationService(TenantAttributes attributes, Context context,
    Map<String, String> okapiHeaders) {

    this.attributes = attributes;
    okapiClient = new OkapiClient(context.owner(), okapiHeaders);
    postgresClient = PgUtil.postgresClient(context, okapiHeaders);
    schemaName = convertToPsqlStandard(tenantId(okapiHeaders));
    errorMessages = new ArrayList<>();
  }

  public Boolean shouldMigrate(String moduleVersion) {
    if (attributes.getModuleFrom() != null && attributes.getModuleTo() != null) {
      SemVer migrationModuleVersion = moduleVersionToSemVer(moduleVersion);
      SemVer moduleFromVersion = moduleVersionToSemVer(attributes.getModuleFrom());
      SemVer moduleToVersion = moduleVersionToSemVer(attributes.getModuleTo());

      if (moduleToVersion.compareTo(migrationModuleVersion) < 0) {
        log.info("skipping migration for module version {}: should be {} or higher",
          moduleToVersion, migrationModuleVersion);
        return false;
      }

      if (moduleFromVersion.compareTo(migrationModuleVersion) >= 0) {
        log.info("skipping migration for module version {}: previous version {} is already migrated",
          moduleToVersion, moduleFromVersion);
        return false;
      }
    }
    else {
      log.info("skipping migration - can not determine current moduleFrom or moduleTo version");
      return false;
    }

    return true;
  }

  public Future<Integer> getBatchCount() {
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

  private static SemVer moduleVersionToSemVer(String version) {
    try {
      return new SemVer(version);
    } catch (IllegalArgumentException ex) {
      return new ModuleId(version).getSemVer();
    }
  }
}
