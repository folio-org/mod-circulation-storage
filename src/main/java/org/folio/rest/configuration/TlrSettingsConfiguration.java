package org.folio.rest.configuration;

import static org.folio.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.support.JsonPropertyFetcher.getUUIDProperty;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class TlrSettingsConfiguration {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final boolean titleLevelRequestsFeatureEnabled;
  private final boolean createTitleLevelRequestsByDefault;
  private final UUID confirmationPatronNoticeTemplateId;
  private final UUID cancellationPatronNoticeTemplateId;
  private final UUID expirationPatronNoticeTemplateId;

  public static TlrSettingsConfiguration from(JsonObject jsonObject) {
    try {
      return new TlrSettingsConfiguration(
        getBooleanProperty(jsonObject, "titleLevelRequestsFeatureEnabled"),
        getBooleanProperty(jsonObject, "createTitleLevelRequestsByDefault"),
        getUUIDProperty(jsonObject, "confirmationPatronNoticeTemplateId"),
        getUUIDProperty(jsonObject, "cancellationPatronNoticeTemplateId"),
        getUUIDProperty(jsonObject, "expirationPatronNoticeTemplateId")
      );
    }
    catch (IllegalArgumentException e) {
      log.error("Failed to parse TLR setting configuration");
      return null;
    }
  }
}
