package org.folio.rest.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static org.folio.rest.jaxrs.resource.TlrFeatureToggleJobStart.PostTlrFeatureToggleJobStartResponse.respond202;
import static org.folio.rest.jaxrs.resource.TlrFeatureToggleJobStart.PostTlrFeatureToggleJobStartResponse.respond422WithApplicationJson;
import static org.folio.rest.jaxrs.resource.TlrFeatureToggleJobStart.PostTlrFeatureToggleJobStartResponse.respond500WithTextPlain;
import static org.folio.support.ModuleConstants.TLR_FEATURE_TOGGLE_JOB_STATUS_FIELD;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.TlrFeatureToggleJobRepository;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.jaxrs.resource.TlrFeatureToggleJobStart;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.service.tlr.TlrFeatureToggleService;
import org.folio.support.exception.TlrFeatureToggleJobAlreadyRunningException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class TlrFeatureToggleImpl implements TlrFeatureToggleJobStart {
  private static final Logger log = LogManager.getLogger(TlrFeatureToggleImpl.class);

  @Override
  public void postTlrFeatureToggleJobStart(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> handle(okapiHeaders, vertxContext)
      .onComplete(result -> {
        if (result.succeeded()) {
          asyncResultHandler.handle(succeededFuture(respond202()));
        } else {
          log.error("TLR feature toggle job failed", result.cause());

          if (result.cause() instanceof TlrFeatureToggleJobAlreadyRunningException) {
            Error error = new Error();
            error.setMessage(result.cause().getMessage());

            asyncResultHandler.handle(succeededFuture(respond422WithApplicationJson(
              new Errors().withErrors(singletonList(error)))));
          } else {
            asyncResultHandler.handle(
              succeededFuture(respond500WithTextPlain(result.cause().getMessage())));
          }
        }
      })
    );
  }

  private Future<Void> handle(Map<String, String> okapiHeaders, Context vertxContext) {
    TlrFeatureToggleJobRepository repository = new TlrFeatureToggleJobRepository(vertxContext,
      okapiHeaders);

    return refuseWhenJobsInProgressExist(repository)
      .compose(v -> findJobsByStatus(repository, TlrFeatureToggleJob.Status.OPEN.value()))
      .compose(openJobs -> runJobAndReturnImmediately(repository, openJobs));
  }

  private Future<Void> runJobAndReturnImmediately(TlrFeatureToggleJobRepository repository,
    List<TlrFeatureToggleJob> openJobs) {

    this.run(repository, openJobs);
    return succeededFuture();
  }

  private Future<List<TlrFeatureToggleJob>> findJobsByStatus(
    TlrFeatureToggleJobRepository repository, String status) {

    return repository.get(new Criterion()
      .addCriterion(new Criteria()
        .addField(TLR_FEATURE_TOGGLE_JOB_STATUS_FIELD)
        .setOperation("=")
        .setVal(status)));
  }

  private Future<Void> run(TlrFeatureToggleJobRepository repository,
    List<TlrFeatureToggleJob> openJobs) {

    if (openJobs.isEmpty()) {
      return succeededFuture();
    }

    return new TlrFeatureToggleService(repository).run(openJobs.get(0));
  }

  private Future<Void> refuseWhenJobsInProgressExist(TlrFeatureToggleJobRepository repository) {
    return findJobsByStatus(repository, TlrFeatureToggleJob.Status.IN_PROGRESS.value())
      .compose(jobList -> jobList.isEmpty()
        ? succeededFuture()
        : failedFuture(new TlrFeatureToggleJobAlreadyRunningException(jobList)));
  }
}
