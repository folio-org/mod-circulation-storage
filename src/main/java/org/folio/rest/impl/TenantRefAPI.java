package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.support.kafka.topic.CirculationStorageKafkaTopic;
import org.folio.dbschema.ObjectMapperTool;
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
import io.vertx.core.Vertx;

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

    Vertx vertx = vertxContext.owner();

    return (new TlrDataMigrationService(attributes, vertxContext, headers).migrate())
      .compose(f -> new RequestSearchFieldsMigrationService(attributes, vertxContext, headers).migrate())
      .compose(r -> new KafkaAdminClientService(vertxContext.owner())
        .createKafkaTopics(CirculationStorageKafkaTopic.values(), tenantId))
      .compose(r -> super.loadData(attributes, tenantId, headers, vertxContext))
      .compose(r -> loadData(attributes, headers, vertx))
      .compose(r -> registerPubSub(headers, vertx))
      .mapEmpty();
  }

  /**
   * (attributes.getModuleFrom() == null || attributes.getModuleFrom() < featureVersion)
   * with semantic version comparison.
   */
  public static boolean isNew(TenantAttributes attributes, String featureVersionString) {
    if (attributes.getModuleFrom() == null) {
      return true;
    }
    var featureVersion = new Versioned() {
    };
    featureVersion.setFromModuleVersion(featureVersionString);
    return featureVersion.isNewForThisInstall(attributes.getModuleFrom());
  }

  private Future<Integer> loadData(TenantAttributes attributes, Map<String, String> headers, Vertx vertx) {
    log.info("Initializing of tenant's data");
    TenantLoading tl = new TenantLoading();
    if (isNew(attributes, "14.0.0")) {
      tl.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD)
        .withIdContent()
        .add("loan-policy-storage/loan-policies")
        .add("request-policy-storage/request-policies")
        .add("patron-notice-policy-storage/patron-notice-policies")
        .add("staff-slips-storage/staff-slips")
        .withIdRaw()
        .add("circulation-rules-storage")
        .withIdContent()
        .add("cancellation-reason-storage/cancellation-reasons");
    }
    if (isNew(attributes, "7.0.0")) {
      tl.withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD)
        .add("loans", "loan-storage/loans");
    }
    return Future.future(promise -> tl.perform(attributes, headers, vertx, promise));
  }

  private Future<Void> registerPubSub(Map<String, String> headers, Vertx vertx) {
    return Future.fromCompletionStage(PubSubRegistrationService.registerModule(headers, vertx)
        .handle((aBoolean, throwable) -> null));
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
