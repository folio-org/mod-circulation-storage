package org.folio.rest.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static java.lang.String.format;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.configuration.TlrSettingsConfiguration;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.KvConfigurations;
import org.folio.support.exception.HttpException;
import org.folio.util.StringUtil;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class ConfigurationClient extends OkapiClient {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String CONFIGURATIONS_URL = "/configurations/entries?query=%s";
  private static final String TLR_SETTINGS_QUERY = "module==\"SETTINGS\" and configName==\"TLR\"";
  private static final String url = format(CONFIGURATIONS_URL, StringUtil.urlEncode(TLR_SETTINGS_QUERY));

  public ConfigurationClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<TlrSettingsConfiguration> getTlrSettingsOrDefault() {
    return getSettingsFromStorage(url)
    .compose(this::tryParseResponseToJson)
    .compose(res -> {
      log.debug("getTlrSettingsOrDefault:: Checking for existence of TLR settings");
      if (res.getConfigs().isEmpty()) {
        log.debug("getTlrSettingsOrDefault:: TLR settings empty, using default settings");
        return succeededFuture(new TlrSettingsConfiguration(false, false, null, null, null));
      } else {
        log.debug("getTlrSettingsOrDefault:: TLR settings found");
        return parseSettings(res);
      }
    });
  }

  public Future<TlrSettingsConfiguration> getTlrSettings() {
    return getSettingsFromStorage(url)
      .compose(this::tryParseResponseToJson)
      .compose(this::parseSettings);
  }

  private Future<KvConfigurations> tryParseResponseToJson(String settings) {
    log.info("setting string: " + settings);
    try{ 
      return succeededFuture(objectMapper.readValue(settings, KvConfigurations.class));
    } catch (JsonProcessingException e) {
      log.error("Failed to parse response: {}", settings);
      return failedFuture(e);
    }
  }

  private Future<TlrSettingsConfiguration> parseSettings(KvConfigurations settings) {
      return settings
        .getConfigs().stream()
        .findFirst()
        .map(Config::getValue)
        .map(JsonObject::new)
        .map(TlrSettingsConfiguration::from)
        .map(Future::succeededFuture)
        .orElseGet(() -> failedFuture("Failed to find TLR configuration"));
  }

  private Future<String> getSettingsFromStorage(String url) {
    return okapiGet(url)
      .compose(response -> {
        int responseStatus = response.statusCode();
        if (responseStatus != 200) {
          String errorMessage = format("Failed to find TLR configuration. Response: %d %s",
            responseStatus, response.bodyAsString());
          log.error(errorMessage);
          return failedFuture(new HttpException(GET, url, response));
        } else {
          return succeededFuture(response.bodyAsString());
        }
    });
  }
}
