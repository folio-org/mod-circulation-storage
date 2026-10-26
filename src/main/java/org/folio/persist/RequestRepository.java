package org.folio.persist;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.support.ModuleConstants.REQUEST_CLASS;
import static org.folio.support.ModuleConstants.REQUEST_TABLE;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Conn;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class RequestRepository extends AbstractRepository<Request> {

  public RequestRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), REQUEST_TABLE, REQUEST_CLASS);
  }

  public Future<Void> updatePositions(List<Request> requests) {
    if (requests.isEmpty()) {
      return succeededFuture();
    }

    return postgresClient.withTrans(conn -> clearPositions(conn, requests)
      .compose(v -> updatePositions(conn, requests)));
  }

  private Future<Void> clearPositions(Conn conn, List<Request> requests) {
    String requestIds = requests.stream()
      .map(Request::getId)
      .map(UUID::fromString)
      .map(id -> "'" + id + "'")
      .collect(Collectors.joining(","));
    String table = convertToPsqlStandard(postgresClient.getTenantId()) + "." + REQUEST_TABLE;

    return conn.execute(format(
      "UPDATE %s SET jsonb = jsonb - 'position' WHERE id::text IN (%s)",
      table, requestIds)).mapEmpty();
  }

  private Future<Void> updatePositions(Conn conn, List<Request> requests) {
    Future<Void> future = succeededFuture();
    for (Request request : requests) {
      future = future.compose(v -> conn.update(REQUEST_TABLE, request, request.getId()).mapEmpty());
    }
    return future;
  }

}
