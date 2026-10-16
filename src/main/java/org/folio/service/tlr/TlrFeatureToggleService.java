package org.folio.service.tlr;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_DELIVERY;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_PICKUP;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.DONE;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.FAILED;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.IN_PROGRESS;
import static org.folio.support.ModuleConstants.REQUEST_STATUS_FIELD;
import static org.folio.support.ModuleConstants.TLR_FEATURE_TOGGLE_JOB_STATUS_FIELD;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestRepository;
import org.folio.persist.TlrFeatureToggleJobRepository;
import org.folio.rest.configuration.TlrSettings;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.GroupedCriterias;
import org.folio.rest.persist.Criteria.Order;
import org.folio.service.CirculationSettingsService;
import org.folio.service.RequestQueueLockService;
import org.folio.support.exception.TlrFeatureToggleJobAlreadyRunningException;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class TlrFeatureToggleService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final List<Request.Status> OPEN_REQUEST_STATUSES = List.of(
    OPEN_NOT_YET_FILLED, OPEN_AWAITING_PICKUP, OPEN_IN_TRANSIT, OPEN_AWAITING_DELIVERY);

  private final TlrFeatureToggleJobRepository tlrFeatureToggleJobRepository;
  private final RequestRepository requestRepository;
  private final CirculationSettingsService circulationSettingsService;
  private final RequestQueueLockService requestQueueLockService;

  public TlrFeatureToggleService(Map<String, String> okapiHeaders, Context vertxContext) {
    this.tlrFeatureToggleJobRepository = new TlrFeatureToggleJobRepository(vertxContext,
      okapiHeaders);
    this.requestRepository = new RequestRepository(vertxContext, okapiHeaders);
    this.circulationSettingsService = new CirculationSettingsService(vertxContext, okapiHeaders);
    this.requestQueueLockService = new RequestQueueLockService(
      postgresClient(vertxContext, okapiHeaders), vertxContext.owner());
  }

  public Future<Void> handle() {
    return succeededFuture()
      .compose(v -> refuseWhenJobsInProgressExist())
      .compose(v -> findJobsByStatus(TlrFeatureToggleJob.Status.OPEN.value()))
      .compose(this::run);
  }

  private Future<Void> run(List<TlrFeatureToggleJob> openJobs) {
    return openJobs.stream()
      .findFirst()
      .map(this::run)
      .orElse(succeededFuture());
  }

  private Future<Void> run(TlrFeatureToggleJob job) {
    log.info("Processing TLR feature toggle job {}", job.getId());

    return succeededFuture(job)
      .compose(j -> updateJobStatus(job, IN_PROGRESS))
      .compose(r -> circulationSettingsService.getTlrSettingsOrThrow())
      .compose(settings -> fetchAndGroupOpenRequests(settings)
        .compose(groupedRequests -> updatePositions(groupedRequests.keySet(), settings, job)))
      .compose(r -> updateJobStatus(job, DONE))
      .recover(throwable -> updateJobAsFailed(job, throwable))
      .mapEmpty();
  }

  private Future<Map<String, List<Request>>> fetchAndGroupOpenRequests(
    TlrSettings tlrSettingsConfiguration) {

    return fetchOpenRequests()
      .map(requests -> groupRequests(requests, tlrSettingsConfiguration));
  }

  private Map<String, List<Request>> groupRequests(List<Request> requests,
    TlrSettings tlrSettingsConfiguration) {

    return requests.stream()
      .collect(Collectors.groupingBy(tlrSettingsConfiguration.isTitleLevelRequestsFeatureEnabled()
        ? Request::getInstanceId
        : Request::getItemId));
  }

  private Future<List<Request>> fetchOpenRequests() {
    return requestRepository.get(new Criterion().addGroupOfCriterias(
      buildGroupedCriterias(getStatusCriterias())));
  }

  private List<Criteria> getStatusCriterias() {
    return OPEN_REQUEST_STATUSES.stream()
      .map(status -> List.of(
        new Criteria()
          .addField(REQUEST_STATUS_FIELD)
          .setOperation("=")
          .setVal(status.toString())))
      .flatMap(Collection::stream)
      .collect(toList());
  }

  private GroupedCriterias buildGroupedCriterias(List<Criteria> criterias) {
    GroupedCriterias groupedCriterias = new GroupedCriterias();
    criterias.forEach(criteria -> groupedCriterias.addCriteria(criteria, "OR"));

    return groupedCriterias;
  }

  private Future<Void> updatePositions(Set<String> queueIds, TlrSettings tlrSettings,
    TlrFeatureToggleJob job) {

    String queueType = tlrSettings.isTitleLevelRequestsFeatureEnabled()
      ? RequestQueueLockService.INSTANCE
      : RequestQueueLockService.ITEM;
    String queueIdField = tlrSettings.isTitleLevelRequestsFeatureEnabled()
      ? "instanceId"
      : "itemId";
    int[] updatedRequestCount = {0};
    Future<Void> future = succeededFuture();

    for (String queueId : queueIds) {
      future = future.compose(v -> requestQueueLockService.execute(queueType, queueId,
        () -> fetchOpenRequests(queueIdField, queueId)
          .compose(requests -> {
            updatedRequestCount[0] += requests.size();
            return requests.isEmpty()
              ? succeededFuture()
              : requestRepository.update(updatePositions(requests)).mapEmpty();
          })));
    }

    return future.onSuccess(v -> job.setNumberOfUpdatedRequests(updatedRequestCount[0]));
  }

  private List<Request> updatePositions(List<Request> requests) {
    requests.sort(Comparator.comparingInt(Request::getPosition));
    int position = 1;
    for (Request request : requests) {
      request.setPosition(position++);
    }
    return requests;
  }

  private Future<List<Request>> fetchOpenRequests(String queueIdField, String queueId) {
    return requestRepository.get(new Criterion()
      .addGroupOfCriterias(buildGroupedCriterias(getStatusCriterias()))
      .addCriterion(new Criteria()
        .addField("'" + queueIdField + "'")
        .setOperation("=")
        .setVal(queueId)));
  }

  private Future<List<TlrFeatureToggleJob>> findJobsByStatus(String status) {
    return tlrFeatureToggleJobRepository.get(new Criterion()
      .addCriterion(new Criteria()
        .addField(TLR_FEATURE_TOGGLE_JOB_STATUS_FIELD)
        .setOperation("=")
        .setVal(status))
      .setOrder(new Order(String.format("jsonb->>'%s'", "metadata,createdDate"),
        Order.ORDER.DESC)));
  }

  private Future<Void> refuseWhenJobsInProgressExist() {
    return findJobsByStatus(IN_PROGRESS.value())
      .compose(jobList -> jobList.isEmpty()
        ? succeededFuture()
        : failedFuture(new TlrFeatureToggleJobAlreadyRunningException(jobList)));
  }

  private Future<RowSet<Row>> updateJobAsFailed(TlrFeatureToggleJob job,
    Throwable throwable) {

    job.getErrors().add(throwable.getLocalizedMessage());

    return updateJobStatus(job, FAILED);
  }

  private Future<RowSet<Row>> updateJobStatus(TlrFeatureToggleJob job,
    TlrFeatureToggleJob.Status status) {

    return tlrFeatureToggleJobRepository.update(job.getId(), job.withStatus(status));
  }
}
