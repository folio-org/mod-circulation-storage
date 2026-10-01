package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AnonymizationDueDateClearRequest;
import org.folio.rest.jaxrs.model.AnonymizationDueDateResponse;
import org.folio.rest.jaxrs.model.AnonymizationDueDateStampRequest;
import org.folio.rest.jaxrs.model.Loans;
import org.folio.rest.jaxrs.resource.AnonymizationDueDateStorage;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.service.anonymization.AnonymizationDueDateService;
import org.folio.service.anonymization.AnonymizationDueDateService.StampEntry;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Endpoints for the {@code loan_anonymization_due} table. See
 * {@link AnonymizationDueDateService} for semantics.
 */
public class AnonymizationDueDateAPI implements AnonymizationDueDateStorage {
  private static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  public void postAnonymizationDueDateStorageStamp(
    AnonymizationDueDateStampRequest request, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    final List<StampEntry> entries = request.getEntries().stream()
      .map(e -> new StampEntry(e.getLoanId(), e.getDueAt()))
      .toList();

    log.info("postAnonymizationDueDateStorageStamp:: stamping {} loans", entries.size());

    new AnonymizationDueDateService(vertxContext, okapiHeaders)
      .stamp(entries)
      .onSuccess(updated -> asyncResultHandler.handle(succeededFuture(
        PostAnonymizationDueDateStorageStampResponse.respond200WithApplicationJson(
          new AnonymizationDueDateResponse().withUpdated(updated)))))
      .onFailure(t -> asyncResultHandler.handle(succeededFuture(mapStampFailure(t))));
  }

  private Response mapStampFailure(Throwable t) {
    if (t instanceof IllegalArgumentException) {
      return PostAnonymizationDueDateStorageStampResponse.respond422WithApplicationJson(
        ValidationHelper.createValidationErrorMessage("entries", "", t.getMessage()));
    }
    log.error("mapStampFailure:: stamp failed", t);
    return PostAnonymizationDueDateStorageStampResponse.respond500WithTextPlain(t.getMessage());
  }

  @Validate
  @Override
  public void postAnonymizationDueDateStorageClear(
    AnonymizationDueDateClearRequest request, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    final boolean byLoans = request.getLoanIds() != null && !request.getLoanIds().isEmpty();
    final boolean all = Boolean.TRUE.equals(request.getAll());

    if ((byLoans ? 1 : 0) + (all ? 1 : 0) != 1) {
      asyncResultHandler.handle(succeededFuture(
        PostAnonymizationDueDateStorageClearResponse.respond422WithApplicationJson(
          ValidationHelper.createValidationErrorMessage("loanIds|all", "",
            "Exactly one of loanIds, all must be provided"))));
      return;
    }

    final AnonymizationDueDateService service =
      new AnonymizationDueDateService(vertxContext, okapiHeaders);

    final io.vertx.core.Future<Integer> result = byLoans
      ? service.clearByLoanIds(request.getLoanIds())
      : service.clearAll();

    result
      .onSuccess(cleared -> {
        log.info("postAnonymizationDueDateStorageClear:: cleared {} rows", cleared);
        asyncResultHandler.handle(succeededFuture(
          PostAnonymizationDueDateStorageClearResponse.respond200WithApplicationJson(
            new AnonymizationDueDateResponse().withUpdated(cleared))));
      })
      .onFailure(t -> asyncResultHandler.handle(succeededFuture(mapClearFailure(t))));
  }

  private Response mapClearFailure(Throwable t) {
    if (t instanceof IllegalArgumentException) {
      return PostAnonymizationDueDateStorageClearResponse.respond422WithApplicationJson(
        ValidationHelper.createValidationErrorMessage("loanIds|all", "", t.getMessage()));
    }
    log.error("mapClearFailure:: clear failed", t);
    return PostAnonymizationDueDateStorageClearResponse.respond500WithTextPlain(t.getMessage());
  }

  @Validate
  @Override
  public void getAnonymizationDueDateStorageDue(int limit, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new AnonymizationDueDateService(vertxContext, okapiHeaders).findDue(limit)
      .onSuccess(loans -> asyncResultHandler.handle(succeededFuture(
        GetAnonymizationDueDateStorageDueResponse.respond200WithApplicationJson(toLoans(loans)))))
      .onFailure(t -> {
        log.error("getAnonymizationDueDateStorageDue:: finder failed", t);
        asyncResultHandler.handle(succeededFuture(
          GetAnonymizationDueDateStorageDueResponse.respond500WithTextPlain(t.getMessage())));
      });
  }

  @Validate
  @Override
  public void getAnonymizationDueDateStorageUnevaluated(int limit, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new AnonymizationDueDateService(vertxContext, okapiHeaders).findUnevaluated(limit)
      .onSuccess(loans -> asyncResultHandler.handle(succeededFuture(
        GetAnonymizationDueDateStorageUnevaluatedResponse.respond200WithApplicationJson(toLoans(loans)))))
      .onFailure(t -> {
        log.error("getAnonymizationDueDateStorageUnevaluated:: finder failed", t);
        asyncResultHandler.handle(succeededFuture(
          GetAnonymizationDueDateStorageUnevaluatedResponse.respond500WithTextPlain(t.getMessage())));
      });
  }

  /**
   * The finders return whole loans (not ids) so the scheduled job needs no
   * follow-up fetch.
   */
  private Loans toLoans(JsonArray loans) {
    return new JsonObject()
      .put("loans", loans)
      .put("totalRecords", loans.size())
      .mapTo(Loans.class);
  }
}
