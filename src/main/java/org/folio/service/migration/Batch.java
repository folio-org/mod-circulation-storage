package org.folio.service.migration;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;

import org.folio.rest.persist.Conn;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class Batch<T extends RequestMigrationContext> {
  private final int batchNumber;
  private final Conn connection;
  private List<T> requestMigrationContexts = new ArrayList<>();

  @Override
  public String toString() {
    return format("Batch(batchNumber=%d, numberOfRequests=%d)", batchNumber,
      requestMigrationContexts.size());
  }
}