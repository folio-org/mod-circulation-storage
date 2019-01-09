package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.PatronNoticePolicy;
import org.folio.rest.jaxrs.resource.PatronNoticePolicyStorage;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

public class PatronNoticePoliciesAPI implements PatronNoticePolicyStorage {

  private static final Logger logger = LoggerFactory.getLogger(PatronNoticePoliciesAPI.class);

  private static final String PATRON_NOTICE_POLICY_TABLE = "patron_notice_policy";

  @Override
  public void postPatronNoticePolicyStoragePatronNoticePolicies(
    PatronNoticePolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
        PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);

        if (entity.getId() == null) {
          entity.setId(UUID.randomUUID().toString());
        }

        pgClient.save(PATRON_NOTICE_POLICY_TABLE, entity.getId(), entity, save -> {
          if (save.failed()) {
            logger.error(save.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              PostPatronNoticePolicyStoragePatronNoticePoliciesResponse.respond500WithTextPlain(save.cause())));
            return;
          }
          asyncResultHandler.handle(Future.succeededFuture(
            PostPatronNoticePolicyStoragePatronNoticePoliciesResponse.respond201WithApplicationJson(entity)));
        });
      } catch (Exception e) {
        logger.error(e);
        asyncResultHandler.handle(Future.succeededFuture(
          PostPatronNoticePolicyStoragePatronNoticePoliciesResponse.respond500WithTextPlain(e)));
      }
    });
  }

  @Override
  public void putPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyId(
    String patronNoticePolicyId,
    PatronNoticePolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
        PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);

        pgClient.update(PATRON_NOTICE_POLICY_TABLE, entity, patronNoticePolicyId, update -> {
          if (update.failed()) {
            logger.error(update.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              PutPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse
                .respond500WithTextPlain(update.cause())));
            return;
          }
          asyncResultHandler.handle(Future.succeededFuture(
            PutPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse.respond204()));
        });
      } catch (Exception e) {
        logger.error(e);
        asyncResultHandler.handle(Future.succeededFuture(
          PutPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse.respond500WithTextPlain(e)));
      }
    });
  }
}
