package org.folio.service.tlr;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.model.Request.Status.OPEN_NOT_YET_FILLED;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestRepository;
import org.folio.persist.TlrFeatureToggleJobRepository;
import org.folio.rest.client.ConfigurationClient;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class TlrFeatureToggleService {
  private static final Logger log = LogManager.getLogger(TlrFeatureToggleService.class);

  private final TlrFeatureToggleJobRepository tlrFeatureToggleJobRepository;
  private final RequestRepository requestRepository;
  private final ConfigurationClient configurationClient;

  public TlrFeatureToggleService(Map<String, String> okapiHeaders, Context vertxContext) {
    this.tlrFeatureToggleJobRepository = new TlrFeatureToggleJobRepository(vertxContext,
      okapiHeaders);
    this.requestRepository = new RequestRepository(vertxContext, okapiHeaders);
    this.configurationClient = new ConfigurationClient(vertxContext.owner(), okapiHeaders);
  }

  public Future<Void> run(TlrFeatureToggleJob job) {
    log.info("Processing job {}", job.getId());

    job.setStatus(TlrFeatureToggleJob.Status.IN_PROGRESS);

    configurationClient.getTlrSettings()
      .compose(tlrSettings -> tlrSettings.isTitleLevelRequestsFeatureEnabled()
        ? fetchOpenRequests().compose(requests -> groupRequestsBy(requests, Request::getInstanceId))
        : fetchOpenRequests().compose(requests -> groupRequestsBy(requests, Request::getItemId)))
      .compose(this::updatePosition)
      .compose(this::updateRequests);

    return tlrFeatureToggleJobRepository.update(job.getId(), job)
      .mapEmpty();
  }

  private Future<Map<String, List<Request>>> groupRequestsBy(List<Request> requests,
    Function<Request, String> groupingFunction) {
    return succeededFuture(requests.stream()
      .collect(Collectors.groupingBy(groupingFunction)));
  }

  private Future<List<Request>> fetchOpenRequests() {
    return requestRepository.get(new Criterion()
      .addCriterion(new Criteria()
        .addField("'status'")
        .setOperation("=")
        .setVal(OPEN_NOT_YET_FILLED.toString())));
  }

  private Future<List<Request>> updatePosition(Map<String, List<Request>> groupedRequests) {
    List<Request> updatedRequests = new ArrayList<>();
      for (var entry : groupedRequests.entrySet()) {
        List<Request> requests = entry.getValue();
        int position = 1;
        for (var request : requests) {
          request.setPosition(position++);
        }
        updatedRequests.addAll(requests);
      }

    return succeededFuture(updatedRequests);
  }

  private Future<Void> updateRequests(List<Request> updatedRequests) {
    return requestRepository.update(updatedRequests)
      .compose(v -> null);
  }
}
