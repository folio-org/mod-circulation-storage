package org.folio.service.tlr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.TlrFeatureToggleJobRepository;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;

import io.vertx.core.Future;

public class TlrFeatureToggleService {
  private static final Logger log = LogManager.getLogger(TlrFeatureToggleService.class);

  private final TlrFeatureToggleJobRepository repository;

  public TlrFeatureToggleService(TlrFeatureToggleJobRepository repository) {
    this.repository = repository;
  }

  public Future<Void> run(TlrFeatureToggleJob job) {
    log.info("Processing job {}", job.getId());

    job.setStatus(TlrFeatureToggleJob.Status.IN_PROGRESS);
    return repository.update(job.getId(), job)
      .mapEmpty();
  }
}
