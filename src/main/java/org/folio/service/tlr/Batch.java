package org.folio.service.tlr;

import java.util.ArrayList;
import java.util.List;

import org.folio.rest.persist.Conn;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class Batch {
  private final int batchNumber;
  private final Conn connection;
  private List<RequestMigrationContext> requestMigrationContexts = new ArrayList<>();

  @Override
  public String toString() {
    return String.format("[batch #%d - %d requests]", batchNumber, requestMigrationContexts.size());
  }
}