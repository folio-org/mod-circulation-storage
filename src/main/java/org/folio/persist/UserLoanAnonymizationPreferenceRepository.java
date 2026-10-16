package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE;

import java.util.Map;

import org.folio.rest.persist.PostgresClient;
import org.folio.support.LoanAnonymizationPreference;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Users whose loan anonymization preference deviates from the library policy.
 */
public class UserLoanAnonymizationPreferenceRepository {

  private final PostgresClient postgresClient;

  public UserLoanAnonymizationPreferenceRepository(Context context,
    Map<String, String> okapiHeaders) {

    postgresClient = postgresClient(context, okapiHeaders);
  }

  public Future<Void> save(String userId, LoanAnonymizationPreference preference) {
    JsonObject row = new JsonObject()
      .put("id", userId)
      .put("preference", preference.value());

    return postgresClient.upsert(USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE, userId, row)
      .mapEmpty();
  }

  public Future<Void> delete(String userId) {
    return postgresClient.delete(USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE, userId)
      .mapEmpty();
  }
}
