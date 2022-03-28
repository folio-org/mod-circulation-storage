package org.folio.rest.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.ScheduledRequestExpiration.ScheduledRequestExpirationResponse.respond204;
import static org.folio.rest.jaxrs.resource.ScheduledRequestExpiration.ScheduledRequestExpirationResponse.respond500WithTextPlain;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.persist.TlrFeatureToggleJobRepository;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.jaxrs.resource.TlrFeatureToggle;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.service.tlr.TlrFeatureToggleService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class TlrFeatureToggleImpl implements TlrFeatureToggle {
  public static final String STATUS_FIELD = "'status'";

  // Use enum instead if it's added in CIRCSTORE-327
  public static final String STATUS_OPEN = "Open";
  public static final String STATUS_IN_PROGRESS = "In progress";

  @Override
  public void tlrFeatureToggle(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> handle(okapiHeaders, vertxContext)
      .onComplete(result -> {
        if (result.succeeded()) {
          asyncResultHandler.handle(succeededFuture(respond204()));
        } else {
          // TODO: handle custom exception separately and return 422 if in-progress jobs exist
          asyncResultHandler.handle(
            succeededFuture(respond500WithTextPlain(result.cause().getMessage())));
        }
      })
    );
  }

  private Future<Void> handle(Map<String, String> okapiHeaders, Context vertxContext) {
    TlrFeatureToggleJobRepository repository = new TlrFeatureToggleJobRepository(vertxContext,
      okapiHeaders);

    return refuseWhenJobsInProgressExist(repository)
      .compose(v -> findJobsByStatus(repository, STATUS_OPEN))
      .compose(this::runJob);
  }

  private Future<List<TlrFeatureToggleJob>> findJobsByStatus(
    TlrFeatureToggleJobRepository repository, String status) {

    return repository.get(new Criterion()
      .addCriterion(new Criteria()
        .addField(STATUS_FIELD)
        .setOperation("=")
        .setVal(status)));
  }

  private Future<Void> runJob(List<TlrFeatureToggleJob> openJobs) {
    if (openJobs.isEmpty()) {
      return succeededFuture();
    }

    TlrFeatureToggleService service = new TlrFeatureToggleService();
    return service.run(openJobs.get(0));
  }

  private Future<Void> refuseWhenJobsInProgressExist(TlrFeatureToggleJobRepository repository) {
    return findJobsByStatus(repository, STATUS_IN_PROGRESS)
      .map(List::isEmpty)
      .compose(empty -> empty
        ? succeededFuture()
        // TODO: custom exception
        : failedFuture(new Exception("Can not run TLR feature toggle job: there is a job already " +
        "in progress")));
  }
}
