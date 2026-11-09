package org.folio.rest.impl;

import static org.folio.rest.jaxrs.resource.LoanAnonymizationPreferenceStorage.PutLoanAnonymizationPreferenceStorageLoanAnonymizationPreferencesResponse.respond204;
import static org.folio.rest.jaxrs.resource.LoanAnonymizationPreferenceStorage.PutLoanAnonymizationPreferenceStorageLoanAnonymizationPreferencesResponse.respond500WithTextPlain;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.UserLoanAnonymizationPreferenceRepository;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.UserLoanAnonymizationPreferences;
import org.folio.rest.jaxrs.resource.LoanAnonymizationPreferenceStorage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

/**
 * Full replace of the user loan anonymization preference table, used by the nightly resync of
 * mod-circulation to repair what the users topic events missed.
 */
public class LoanAnonymizationPreferencesAPI implements LoanAnonymizationPreferenceStorage {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final int QUERY_TIMEOUT_MILLIS = 60_000;

  @Validate
  @Override
  public void putLoanAnonymizationPreferenceStorageLoanAnonymizationPreferences(
    UserLoanAnonymizationPreferences entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new UserLoanAnonymizationPreferenceRepository(vertxContext, okapiHeaders, QUERY_TIMEOUT_MILLIS)
      .replaceAll(entity.getPreferences())
      .<Response>map(ignored -> {
        log.info("replacePreferences:: stored {} preferences",
          entity.getPreferences().size());
        return respond204();
      })
      .otherwise(e -> {
        log.error("replacePreferences:: {}", e.getMessage(), e);
        return respond500WithTextPlain("Internal server error, see log");
      })
      .onComplete(asyncResultHandler);
  }
}
