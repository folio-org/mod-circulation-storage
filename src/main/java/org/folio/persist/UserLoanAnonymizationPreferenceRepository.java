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
  private final int queryTimeoutMillis;

  public UserLoanAnonymizationPreferenceRepository(Context context,
    Map<String, String> okapiHeaders, int queryTimeoutMillis) {

    postgresClient = postgresClient(context, okapiHeaders);
    this.queryTimeoutMillis = queryTimeoutMillis;
  }

  public Future<Void> save(String userId, LoanAnonymizationPreference preference) {
    JsonObject row = new JsonObject()
      .put("id", userId)
      .put("preference", preference.value());

    return postgresClient.withConn(queryTimeoutMillis,
        conn -> conn.upsert(USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE, userId, row))
      .mapEmpty();
  }

  public Future<Void> delete(String userId) {
    return postgresClient.withConn(queryTimeoutMillis,
        conn -> conn.delete(USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE, userId))
      .mapEmpty();
  }
}
