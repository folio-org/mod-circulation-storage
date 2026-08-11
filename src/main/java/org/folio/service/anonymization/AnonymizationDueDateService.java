package org.folio.service.anonymization;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.support.ModuleConstants;
import org.folio.support.UUIDValidation;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

/**
 * Maintenance and finders for the {@code loan_anonymization_due} table. Three
 * row states: absent = unevaluated; {@code due_at} set = due at that instant;
 * {@code due_at} NULL = retain under the current policy. Only the scheduled job
 * calls {@link #stamp}; other callers may only {@code clear}.
 */
public class AnonymizationDueDateService {

  /** Accepted {@code due_at} format; validated before interpolation into SQL. */
  private static final Pattern ISO_INSTANT =
    Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?(Z|[+-]\\d{2}:?\\d{2})");

  private static final String TABLE = "loan_anonymization_due";

  private final PostgresClient postgresClient;
  private final String schemaName;

  public AnonymizationDueDateService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    this.schemaName = format("%s_%s", TenantTool.tenantId(okapiHeaders),
      ModuleConstants.MODULE_NAME);
  }

  /**
   * Upserts one due-date per loan. Only closed loans that still carry a userId
   * are touched (the join drops the rest). A null {@code dueAt} records retain.
   */
  public Future<Integer> stamp(List<StampEntry> entries) {
    for (StampEntry entry : entries) {
      if (!Boolean.TRUE.equals(UUIDValidation.isValidUUID(entry.loanId()))) {
        return Future.failedFuture(new IllegalArgumentException(
          "Invalid loan id: " + entry.loanId()));
      }
      if (entry.dueAt() != null && !ISO_INSTANT.matcher(entry.dueAt()).matches()) {
        return Future.failedFuture(new IllegalArgumentException(
          "dueAt must be an ISO-8601 instant or null: " + entry.dueAt()));
      }
    }
    if (entries.isEmpty()) {
      return Future.succeededFuture(0);
    }

    final String values = entries.stream()
      .map(e -> format("('%s'::uuid, %s)", e.loanId(),
        e.dueAt() == null ? "NULL::timestamptz" : format("'%s'::timestamptz", e.dueAt())))
      .collect(Collectors.joining(", "));

    final String sql = format(
      "INSERT INTO %1$s.%2$s (loan_id, due_at)"
        + " SELECT v.loan_id, v.due_at FROM (VALUES %3$s) AS v(loan_id, due_at)"
        + " JOIN %1$s.loan l ON l.id = v.loan_id"
        // ->> so an explicit JSON null userId is treated as absent.
        + " WHERE l.jsonb->'status'->>'name' = 'Closed' AND l.jsonb->>'userId' IS NOT NULL"
        + " ON CONFLICT (loan_id) DO UPDATE SET due_at = EXCLUDED.due_at",
      schemaName, TABLE, values);

    return postgresClient.execute(sql).map(RowSet::rowCount);
  }

  /** Return specific loans to the unevaluated state. */
  public Future<Integer> clearByLoanIds(List<String> loanIds) {
    for (String loanId : loanIds) {
      if (!Boolean.TRUE.equals(UUIDValidation.isValidUUID(loanId))) {
        return Future.failedFuture(new IllegalArgumentException("Invalid loan id: " + loanId));
      }
    }
    if (loanIds.isEmpty()) {
      return Future.succeededFuture(0);
    }
    final String ids = loanIds.stream()
      .map(id -> "'" + id + "'::uuid")
      .collect(Collectors.joining(", ", "(", ")"));

    return postgresClient.execute(format(
      "DELETE FROM %s.%s WHERE loan_id IN %s", schemaName, TABLE, ids))
      .map(RowSet::rowCount);
  }

  /** Return every loan to the unevaluated state. TRUNCATE, so no dead tuples. */
  public Future<Integer> clearAll() {
    return postgresClient.execute(format("SELECT count(*) FROM %s.%s", schemaName, TABLE))
      .map(rs -> rs.iterator().next().getInteger(0))
      .compose(count -> postgresClient.execute(format("TRUNCATE %s.%s", schemaName, TABLE))
        .map(x -> count));
  }

  /** Closed loans with a userId whose {@code due_at} has arrived, oldest first. */
  public Future<JsonArray> findDue(int limit) {
    return queryLoans(format(
      "SELECT l.jsonb FROM %1$s.loan l"
        + " JOIN %1$s.%2$s d ON d.loan_id = l.id"
        + " WHERE d.due_at IS NOT NULL AND d.due_at < now()"
        + " AND l.jsonb->'status'->>'name' = 'Closed' AND l.jsonb->>'userId' IS NOT NULL"
        + " ORDER BY d.due_at, l.id LIMIT %3$d",
      schemaName, TABLE, limit));
  }

  /** Closed loans with a userId that have no row here yet. */
  public Future<JsonArray> findUnevaluated(int limit) {
    return queryLoans(format(
      "SELECT l.jsonb FROM %1$s.loan l"
        + " LEFT JOIN %1$s.%2$s d ON d.loan_id = l.id"
        + " WHERE d.loan_id IS NULL"
        + " AND l.jsonb->'status'->>'name' = 'Closed' AND l.jsonb->>'userId' IS NOT NULL"
        + " ORDER BY l.id LIMIT %3$d",
      schemaName, TABLE, limit));
  }

  private Future<JsonArray> queryLoans(String sql) {
    return postgresClient.execute(sql).map(rows -> {
      final JsonArray loans = new JsonArray();
      for (Row row : rows) {
        loans.add(row.getJsonObject("jsonb"));
      }
      return loans;
    });
  }

  /** One loan to stamp; a null {@code dueAt} records retain. */
  public record StampEntry(String loanId, String dueAt) { }
}
