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
      .compose(r -> registerModuleInPubSub(headers, vertxContext))
      .mapEmpty();
  }

  private Future<Integer> loadData(TenantAttributes attributes, Map<String, String> headers,
      Context vertxContext) {

    log.info("loadData:: Initializing tenant data");

    TenantLoading tenantLoading = new TenantLoading();
    tenantLoading.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD);
    if (isNew(attributes, "17.4.0")) {
      log.info("loadData:: Adding reference data: loan policies, request policies, " +
        "patron notice policies, staff slips, circulation rules, cancellation reasons");

      tenantLoading.withIdContent()
        .add("loan-policy-storage/loan-policies")
        .add("request-policy-storage/request-policies")
        .add("patron-notice-policy-storage/patron-notice-policies")
        .add("staff-slips-storage/staff-slips")
        .withIdRaw()
        .add("circulation-rules-storage")
        .withIdContent()
        .add("cancellation-reason-storage/cancellation-reasons");
    }
    tenantLoading.withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD);
    if (isNew(attributes, "7.0.0")) {
      log.info("loadData:: Adding sample data: loans, circulation settings");

      tenantLoading.add("loans", "loan-storage/loans")
        .add("circulation-settings-storage/circulation-settings");
    }
    if (isNew(attributes, "16.1.0")) {
      log.info("loadData:: Adding sample data: requests");

      tenantLoading.add("requests", "request-storage/requests");
    }
    return tenantLoading.perform(attributes, headers, vertxContext, 0);
  }

  /**
   * Returns attributes.getModuleFrom() < featureVersion or
   * attributes.getModuleFrom() is null.
   */
  static boolean isNew(TenantAttributes attributes, String featureVersion) {
    log.info("isNew:: params moduleFrom: {}, moduleTo: {}, purge: {}, featureVersion: {}",
      attributes::getModuleFrom, attributes::getModuleTo, attributes::getPurge,
      () -> featureVersion);
    if (attributes.getModuleFrom() == null) {
      log.info("isNew:: moduleFrom is null, quitting");
      return true;
    }
    var since = new Versioned() { };
    since.setFromModuleVersion(featureVersion);
    var result = since.isNewForThisInstall(attributes.getModuleFrom());
    log.info("isNew:: {}", result);
    return result;
  }

  private Future<Boolean> registerModuleInPubSub(Map<String, String> headers, Context vertxContext) {
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
