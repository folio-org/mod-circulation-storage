package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.rest.jaxrs.model.UserLoanAnonymizationPreference;
import org.folio.rest.persist.PostgresClient;
import org.folio.support.LoanAnonymizationPreference;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

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

  public Future<Void> replaceAll(List<UserLoanAnonymizationPreference> preferences) {
    UUID[] userIds = preferences.stream()
      .map(preference -> UUID.fromString(preference.getUserId()))
      .toArray(UUID[]::new);
    String[] values = preferences.stream()
      .map(preference -> preference.getPreference().value())
      .toArray(String[]::new);
    String table = postgresClient.getSchemaName() + "." + USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE;

    return postgresClient.withTrans(queryTimeoutMillis, conn -> conn.execute("DELETE FROM " + table)
        .compose(deleted -> conn.execute("INSERT INTO " + table + " (id, jsonb) "
            + "SELECT id, jsonb_build_object('id', id, 'preference', preference) "
            + "FROM unnest($1::uuid[], $2::text[]) AS input(id, preference)",
          Tuple.tuple().addArrayOfUUID(userIds).addArrayOfString(values))))
      .mapEmpty();
  }
}
