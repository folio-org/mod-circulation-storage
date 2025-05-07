package org.folio.rest.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static java.lang.String.format;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.configuration.TlrSettings;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingsEntries;
import org.folio.support.exception.HttpException;
import org.folio.util.StringUtil;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class SettingsClient extends OkapiClient {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String URL = "/settings/entries?query=" +
    StringUtil.urlEncode("(scope==circulation and key==generalTlr)");

  public SettingsClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<TlrSettings> getTlrSettingsOrDefault() {
    return getTlrSettings(() -> succeededFuture(new TlrSettings(false, false, false)));
  }

  private Future<TlrSettings> getTlrSettings(Supplier<Future<TlrSettings>> otherwise) {
    log.debug("getTlrSettings:: Fetching TLR settings: {}", URL);
    return okapiGet(URL)
      .compose(response -> {
          int responseStatus = response.statusCode();
          log.debug("getTlrSettings:: Response status: {}", responseStatus);
          if (responseStatus != 200) {
            String errorMessage = format("Failed to find TLR configuration. Response: %d %s",
              responseStatus, response.bodyAsString());
            log.error(errorMessage);
            return failedFuture(new HttpException(GET, URL, response));
          } else {
            try {
              return objectMapper.readValue(response.bodyAsString(), SettingsEntries.class)
                .getItems().stream()
                .findFirst()
                .map(Setting::getValue)
                .map(Map.class::cast)
                .map(JsonObject::new)
                .map(TlrSettings::from)
                .map(Future::succeededFuture)
                .orElseGet(otherwise);
            } catch (JsonProcessingException e) {
              log.error("Failed to parse response: {}", response.bodyAsString());
              return failedFuture(e);
            }
          }
        }
      );
  }
}
