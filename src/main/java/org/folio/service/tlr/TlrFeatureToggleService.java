package org.folio.service.tlr;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_DELIVERY;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_AWAITING_PICKUP;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_IN_TRANSIT;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.DONE;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.IN_PROGRESS;
import static org.folio.support.ModuleConstants.STATUS_FIELD;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestRepository;
import org.folio.persist.TlrFeatureToggleJobRepository;
import org.folio.rest.client.ConfigurationClient;
import org.folio.rest.configuration.TlrSettingsConfiguration;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.GroupedCriterias;
import org.folio.support.exception.TlrFeatureToggleJobAlreadyRunningException;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class TlrFeatureToggleService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final List<Request.Status> OPEN_REQUEST_STATUSES = List.of(
    OPEN_NOT_YET_FILLED, OPEN_AWAITING_PICKUP, OPEN_IN_TRANSIT, OPEN_AWAITING_DELIVERY);

  private final TlrFeatureToggleJobRepository tlrFeatureToggleJobRepository;
  private final RequestRepository requestRepository;
  private final ConfigurationClient configurationClient;

  public TlrFeatureToggleService(Map<String, String> okapiHeaders, Context vertxContext) {
    this.tlrFeatureToggleJobRepository = new TlrFeatureToggleJobRepository(vertxContext,
      okapiHeaders);
    this.requestRepository = new RequestRepository(vertxContext, okapiHeaders);
    this.configurationClient = new ConfigurationClient(vertxContext.owner(), okapiHeaders);
  }

  public Future<Void> handle() {
    return refuseWhenJobsInProgressExist()
      .compose(v -> findJobsByStatus(TlrFeatureToggleJob.Status.OPEN.value()))
      .compose(this::runJobAndReturnImmediately);
  }

  private Future<Void> run(TlrFeatureToggleJob job) {
    log.info("Processing job {}", job.getId());

    return succeededFuture(job)
      .compose(j -> tlrFeatureToggleJobRepository.update(job.getId(), job.withStatus(IN_PROGRESS)))
      .compose(r -> configurationClient.getTlrSettings())
      .compose(this::fetchAndGroupOpenRequests)
      .compose(this::updatePosition)
      .compose(requestRepository::update)
      .compose(r -> tlrFeatureToggleJobRepository.update(job.getId(), job.withStatus(DONE)))
      .mapEmpty();
  }

  private Future<Map<String, List<Request>>> fetchAndGroupOpenRequests(
    TlrSettingsConfiguration tlrSettingsConfiguration) {

    return fetchOpenRequests()
      .map(requests -> groupRequests(requests, tlrSettingsConfiguration));
  }

  private Map<String, List<Request>> groupRequests(List<Request> requests,
    TlrSettingsConfiguration tlrSettingsConfiguration) {

    return requests.stream()
      .collect(Collectors.groupingBy(tlrSettingsConfiguration.isTitleLevelRequestsFeatureEnabled()
        ? Request::getInstanceId
        : Request::getItemId));
  }

  private Future<List<Request>> fetchOpenRequests() {
    GroupedCriterias statusCriterias = buildGroupedCriterias(getStatusCriterias(), "OR");

    return requestRepository.get(new Criterion().addGroupOfCriterias(statusCriterias));
//      .addCriterion(new Criteria()
//        .addField(STATUS_FIELD)
//        .setOperation("=")
//        .setVal(OPEN_NOT_YET_FILLED.toString())));
  }

  private List<Criteria> getStatusCriterias() {
    return OPEN_REQUEST_STATUSES.stream()
      .map(status -> List.of(
        new Criteria()
          .addField(STATUS_FIELD)
          .setOperation("=")
          .setVal(status.toString())))
      .flatMap(Collection::stream)
      .collect(toList());
  }

  private GroupedCriterias buildGroupedCriterias(List<Criteria> criterias, String op) {
    GroupedCriterias groupedCriterias = new GroupedCriterias();
    criterias.forEach(criteria -> groupedCriterias.addCriteria(criteria, op));
    return groupedCriterias;
  }

  private Future<List<Request>> updatePosition(Map<String, List<Request>> groupedRequests) {
    List<Request> updatedRequests = new ArrayList<>();
      for (var entry : groupedRequests.entrySet()) {
        List<Request> requests = entry.getValue();
        requests.sort(Comparator.comparingInt(Request::getPosition));
        int position = 1;
        for (var request : requests) {
          request.setPosition(position++);
        }
        updatedRequests.addAll(requests);
      }

    return succeededFuture(updatedRequests);
  }
//
//  private Future<Void> updateRequests(List<Request> updatedRequests) {
//    return requestRepository.update(updatedRequests)
//      .compose(v -> null);
//  }

  private Future<Void> runJobAndReturnImmediately(List<TlrFeatureToggleJob> openJobs) {
    if (openJobs.isEmpty()) {
      return succeededFuture();
    }
    this.run(openJobs);

    return succeededFuture();
  }

  private Future<Void> run(List<TlrFeatureToggleJob> openJobs) {
    if (openJobs.isEmpty()) {
      return succeededFuture();
    }

    return run(openJobs.get(0));
  }

    private Future<List<TlrFeatureToggleJob>> findJobsByStatus(String status) {
    return tlrFeatureToggleJobRepository.get(new Criterion()
      .addCriterion(new Criteria()
        .addField(STATUS_FIELD)
        .setOperation("=")
        .setVal(status)));
  }

  private Future<Void> refuseWhenJobsInProgressExist() {
    return findJobsByStatus(IN_PROGRESS.value())
      .compose(jobList -> jobList.isEmpty()
        ? succeededFuture()
        : failedFuture(new TlrFeatureToggleJobAlreadyRunningException(jobList)));
  }
}
