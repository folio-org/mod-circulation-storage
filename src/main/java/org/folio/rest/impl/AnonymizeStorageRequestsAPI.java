package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.support.ModuleConstants.MODULE_NAME;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AnonymizeStorageRequestsRequest;
import org.folio.rest.jaxrs.model.AnonymizeStorageRequestsResponse;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.NotAnonymizedRequest;
import org.folio.rest.jaxrs.resource.AnonymizeStorageRequests;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.support.UUIDValidation;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import jakarta.validation.constraints.NotNull;

public class AnonymizeStorageRequestsAPI implements AnonymizeStorageRequests {
  private static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  public void postAnonymizeStorageRequests(AnonymizeStorageRequestsRequest request,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> responseHandler, Context vertxContext) {

    AnonymizeStorageRequestsResponse response = new AnonymizeStorageRequestsResponse();
    List<String> requestIds = request.getRequestIds();

    if (requestIds == null || CollectionUtils.isEmpty(requestIds)) {
      final Errors errors = ValidationHelper.createValidationErrorMessage(
          "requestIds", "null", "Please provide valid requestIds");
      responseHandler.handle(succeededFuture(
          PostAnonymizeStorageRequestsResponse.respond422WithApplicationJson(errors)));
      return;
    }

    Map<Boolean, List<String>> requestIdsMap = requestIds.stream()
        .collect(Collectors.groupingBy(UUIDValidation::isValidUUID));

    List<String> validIds = requestIdsMap.get(true);
    List<String> invalidIds = requestIdsMap.get(false);

    if (CollectionUtils.isNotEmpty(invalidIds)) {
      log.warn("postAnonymizeStorageRequests:: Invalid request UUIDs provided: {}", invalidIds);
      addToNotAnonymizedRequests(response, "invalidRequestIds", invalidIds);
    }

    if (CollectionUtils.isEmpty(validIds)) {
      final Errors errors = ValidationHelper.createValidationErrorMessage(
          "requestIds", requestIds.toString(), "Please provide valid requestIds");
      responseHandler.handle(succeededFuture(
          PostAnonymizeStorageRequestsResponse.respond422WithApplicationJson(errors)));
      return;
    }

    log.info("postAnonymizeStorageRequests:: Anonymizing requests: {}", validIds.size());

    final String tenantId = TenantTool.tenantId(okapiHeaders);
    final PostgresClient postgresClient = PgUtil.postgresClient(vertxContext,
        okapiHeaders);

    final String combinedAnonymizationSql = createAnonymizationSQL(validIds,
        tenantId);

    postgresClient.execute(combinedAnonymizationSql).map(
        updateResult -> PostAnonymizeStorageRequestsResponse.respond200WithApplicationJson(
            response.withAnonymizedRequests(validIds)))
        .map(Response.class::cast)
        .otherwise(
            e -> PostAnonymizeStorageRequestsResponse.respond500WithTextPlain(e.getMessage()))
        .onComplete(responseHandler);

  }

  private void addToNotAnonymizedRequests(AnonymizeStorageRequestsResponse response,
      String reason, List<String> ids) {
    List<NotAnonymizedRequest> notAnonymizedRequests = response.getNotAnonymizedRequests();
    notAnonymizedRequests.add(
        new NotAnonymizedRequest().withReason(reason).withRequestIds(ids));
  }

  private String createAnonymizationSQL(@NotNull Collection<String> requestIdList,
      String tenantId) {

    String requestIds = requestIdList.stream()
        .map(s -> "\'" + s + "\'")
        .collect(Collectors.joining(",", "(", ")"));

    return """
        UPDATE %s_%s.request
        SET jsonb = jsonb - ARRAY['requesterId', 'proxyUserId', 'requester', 'proxy']
        WHERE request.id in %s
          AND request.jsonb->>'status' LIKE 'Closed - %%'
          AND (request.jsonb->>'requesterId' is NOT null
            OR request.jsonb->>'proxyUserId' is NOT null
            OR request.jsonb->>'requester' is NOT null
            OR request.jsonb->>'proxy' is NOT null)
        """.formatted(tenantId, MODULE_NAME, requestIds);
  }
}
