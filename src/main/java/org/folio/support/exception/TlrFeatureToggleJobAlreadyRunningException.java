package org.folio.support.exception;

import static java.lang.String.format;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;

public class TlrFeatureToggleJobAlreadyRunningException extends Exception {
  public TlrFeatureToggleJobAlreadyRunningException(List<TlrFeatureToggleJob> jobsInProgress) {
    super(format("Can not run TLR feature toggle job: found job(s) in progress. Job IDs: %s",
      jobsInProgress.stream()
        .map(TlrFeatureToggleJob::getId)
        .collect(Collectors.joining(", "))));
  }
}
