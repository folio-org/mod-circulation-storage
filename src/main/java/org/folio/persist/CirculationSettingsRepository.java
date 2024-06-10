package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.CIRCULATION_SETTINGS_TABLE;

import java.util.Map;
import org.folio.rest.jaxrs.model.CirculationSetting;
import io.vertx.core.Context;

public class CirculationSettingsRepository
  extends AbstractRepository<CirculationSetting> {

  public CirculationSettingsRepository(Context context, Map<String,
    String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), CIRCULATION_SETTINGS_TABLE,
      CirculationSetting.class);
  }

}
