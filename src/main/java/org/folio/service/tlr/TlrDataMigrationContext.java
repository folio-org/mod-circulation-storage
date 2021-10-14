package org.folio.service.tlr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.Promise;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor(force = true)
@Getter
@Setter
public class TlrDataMigrationContext {
  private Promise<Void> migrationProcessPromise;
  private boolean successful = true;
  private List<String> errorMessages = new ArrayList<>();
  private Map<String, String> instanceIds = new HashMap();
}
