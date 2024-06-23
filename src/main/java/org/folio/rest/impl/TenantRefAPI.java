package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.support.kafka.topic.CirculationStorageKafkaTopic;
import org.folio.dbschema.Versioned;
import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.service.PubSubRegistrationService;
import org.folio.service.migration.TlrDataMigrationService;
import org.folio.service.migration.RequestSearchFieldsMigrationService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LogManager.getLogger();
  public static final String REFERENCE_KEY = "loadReference";
  public static final String REFERENCE_LEAD = "ref-data";
  public static final String SAMPLE_KEY = "loadSample";
  public static final String SAMPLE_LEAD = "sample-data";

  @Validate
  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
      Map<String, String> headers, Context vertxContext) {

    return (new TlrDataMigrationService(attributes, vertxContext, headers).migrate())
      .compose(f -> new RequestSearchFieldsMigrationService(attributes, vertxContext, headers).migrate())
      .compose(r -> new KafkaAdminClientService(vertxContext.owner())
        .createKafkaTopics(CirculationStorageKafkaTopic.values(), tenantId))
      .compose(r -> super.loadData(attributes, tenantId, headers, vertxContext))
      .compose(r -> loadData(attributes, headers, vertxContext))
      .compose(r -> registerModule(headers, vertxContext))
      .mapEmpty();
  }

  private Future<Integer> loadData(TenantAttributes attributes, Map<String, String> headers,
      Context vertxContext) {

    log.info("Initializing of tenant's data");
    TenantLoading tl = new TenantLoading();
    tl.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD);
    if (isNew(attributes, "14.0.0")) {
      tl.withIdContent()
        .add("loan-policy-storage/loan-policies")
        .add("request-policy-storage/request-policies")
        .add("patron-notice-policy-storage/patron-notice-policies")
        .add("staff-slips-storage/staff-slips")
        .withIdRaw()
        .add("circulation-rules-storage")
        .withIdContent()
        .add("cancellation-reason-storage/cancellation-reasons");
    }
    tl.withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD);
    if (isNew(attributes, "7.0.0")) {
      tl.add("loans", "loan-storage/loans")
        .add("circulation-settings-storage/circulation-settings");
    }
    if (isNew(attributes, "16.1.0")) {
      tl.add("requests", "request-storage/requests");
    }
    return tl.perform(attributes, headers, vertxContext, 0);
  }

  /**
   * Returns attributes.getModuleFrom() < featureVersion or
   * attributes.getModuleFrom() is null.
   */
  static boolean isNew(TenantAttributes attributes, String featureVersion) {
    if (attributes.getModuleFrom() == null) {
      return true;
    }
    var since = new Versioned() {
    };
    since.setFromModuleVersion(featureVersion);
    return since.isNewForThisInstall(attributes.getModuleFrom());
  }

  private Future<Boolean> registerModule(Map<String, String> headers, Context vertxContext) {
    var vertx = vertxContext.owner();
    return Future.fromCompletionStage(PubSubRegistrationService.registerModule(headers, vertx));
  }

  @Validate
  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers,
                         Handler<AsyncResult<Response>> handler, Context context) {
    // delete Kafka topics if tenant purged
    var tenantId = TenantTool.tenantId(headers);
    Future<Void> result = tenantAttributes.getPurge() != null && tenantAttributes.getPurge()
      ? new KafkaAdminClientService(context.owner()).deleteKafkaTopics(CirculationStorageKafkaTopic.values(), tenantId)
      : Future.succeededFuture();
    result.onComplete(x -> super.postTenant(tenantAttributes, headers, handler, context));
  }

}
