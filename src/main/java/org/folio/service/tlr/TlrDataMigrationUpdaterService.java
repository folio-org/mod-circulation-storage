package org.folio.service.tlr;

public class TlrDataMigrationUpdaterService {
  private final TlrDataMigrationContext migrationContext;

  public TlrDataMigrationUpdaterService(TlrDataMigrationContext migrationContext) {
    this.migrationContext = migrationContext;
  }

  public void update() {
    migrationContext.getMigrationProcessPromise().complete();
  }
}
