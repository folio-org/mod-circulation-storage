package org.folio.support;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public final class DbUtil {
  private DbUtil() {}

  public static Stream<Row> rowSetToStream(RowSet<Row> rowSet) {
    return StreamSupport.stream(rowSet.spliterator(), false);
  }
}
