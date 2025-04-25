package org.folio.rest.configuration;

import static org.folio.support.JsonPropertyFetcher.getBooleanProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class TlrSettings {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final boolean titleLevelRequestsFeatureEnabled;
  private final boolean createTitleLevelRequestsByDefault;
  private final boolean tlrHoldShouldFollowCirculationRules;

  public static TlrSettings from(JsonObject jsonObject) {
    try {
      return new TlrSettings(
        getBooleanProperty(jsonObject, "titleLevelRequestsFeatureEnabled"),
        getBooleanProperty(jsonObject, "createTitleLevelRequestsByDefault"),
        getBooleanProperty(jsonObject, "tlrHoldShouldFollowCirculationRules"));
    }
    catch (IllegalArgumentException e) {
      log.error("Failed to parse TLR setting", e);
      return null;
    }
  }
}
