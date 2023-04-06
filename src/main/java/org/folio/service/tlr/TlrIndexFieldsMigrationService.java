package org.folio.service.tlr;

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
public class TlrIndexFieldsMigrationService extends AbstractRequestMigrationService{
  private static final Logger log = LogManager.getLogger(TlrIndexFieldsMigrationService.class);

  private static final String TLR_MIGRATION_MODULE_VERSION = "mod-circulation-storage-16.1.0";
  private static final String ITEMS_STORAGE_URL = "/item-storage/items";
  private static final String SERVICE_POINT_URL = "/service-points";


  private static final String REQUEST_TABLE = "request";
  private static final String ITEM_KEY = "item";
  private static final String ITEM_ID_KEY = "itemId";

  public TlrIndexFieldsMigrationService(TenantAttributes attributes, Context context,
    Map<String, String> okapiHeaders) {
      super(attributes, context, okapiHeaders, REQUEST_TABLE, TLR_MIGRATION_MODULE_VERSION);
    }

  public Future<Void> processBatch(Batch batch) {
    log.info("{} processing started", batch);

    return succeededFuture(batch)
      .compose(this::fetchRequests)
      .compose(this::validateRequests)
      .compose(this::findServicePointNames)
      .compose(this::findCallNumbers)
      .onSuccess(this::buildNewRequests)
      .compose(this::updateRequests)
      .onSuccess(r -> log.info("{} processing finished successfully", batch))
      .recover(t -> handleError(batch, t));
  }

  public Collection<String> validateRequest(RequestMigrationContext context) {
    final JsonObject request = context.getOldRequest();
    final String requestId = context.getRequestId();
    final List<String> errors = new ArrayList<>();

    return errors;
  }

  private Future<Batch> findServicePointNames(Batch batch) {

    return succeededFuture(batch);
 
  }

  private Future<Batch> findCallNumbers(Batch batch) {

    return succeededFuture(batch);

  }

  public void buildNewRequest(RequestMigrationContext context) {

  }
}
