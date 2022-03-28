package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.TLR_FEATURE_TOGGLE_JOB_CLASS;
import static org.folio.support.ModuleConstants.TLR_FEATURE_TOGGLE_JOB_TABLE;

import java.util.Map;

import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;

import io.vertx.core.Context;

public class TlrFeatureToggleJobRepository extends AbstractRepository<TlrFeatureToggleJob> {

  public TlrFeatureToggleJobRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), TLR_FEATURE_TOGGLE_JOB_TABLE,
      TLR_FEATURE_TOGGLE_JOB_CLASS);
  }

}
