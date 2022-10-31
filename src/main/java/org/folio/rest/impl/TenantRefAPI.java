package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.CirculationStorageKafkaTopic;
import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.service.PubSubRegistrationService;
import org.folio.service.tlr.TlrDataMigrationService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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

    return (new TlrDataMigrationService(attributes, vertxContext, headers).migrate())
      .compose(r -> new KafkaAdminClientService(vertxContext.owner())
        .createKafkaTopics(CirculationStorageKafkaTopic.values(), tenantId))
      .compose(r -> super.loadData(attributes, tenantId, headers, vertxContext))
      .compose(superRecordsLoaded -> {
        log.info("Initializing of tenant's data");
        Vertx vertx = vertxContext.owner();
        Promise<Integer> promise = Promise.promise();
        TenantLoading tl = new TenantLoading();
        tl.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD)
          .withIdContent()
          .add("loan-policy-storage/loan-policies")
          .add("request-policy-storage/request-policies")
          .add("patron-notice-policy-storage/patron-notice-policies")
          .add("staff-slips-storage/staff-slips")
          .withIdRaw()
          .add("circulation-rules-storage")
          .withIdContent()
          .add("cancellation-reason-storage/cancellation-reasons")
          .withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD)
          .add("loans", "loan-storage/loans")
          .add("requests", "request-storage/requests");

        tl.perform(attributes, headers, vertx, res -> {
          if (res.failed()) {
            promise.fail(res.cause());
          } else {
            PubSubRegistrationService.registerModule(headers, vertx)
              .whenComplete((aBoolean, throwable) -> promise.complete());
          }
        });
        return promise.future();
      });
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
