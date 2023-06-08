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
import org.folio.rest.jaxrs.model.TenantJob;
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

  public ConfigurationClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<TlrSettingsConfiguration> getTlrSettings() {
    String url = format(CONFIGURATIONS_URL, StringUtil.urlEncode(TLR_SETTINGS_QUERY));
    return okapiGet(url)
      .compose(response -> {
        int responseStatus = response.statusCode();
        if (responseStatus != 200) {
          String errorMessage = format("Failed to find TLR configuration. Response: %d %s",
            responseStatus, response.bodyAsString());
          log.error(errorMessage);
          return failedFuture(new HttpException(GET, url, response));
        } else {
          JsonObject json = response.bodyAsJsonObject();
          if (json.getInteger("totalRecords")  == 0) {
            TlrSettingsConfiguration config = new TlrSettingsConfiguration(false, false, null, null, null);
            return succeededFuture(config);
          }
          try {
            log.info("response: " + response.bodyAsString());
            return objectMapper.readValue(response.bodyAsString(), KvConfigurations.class)
              .getConfigs().stream()
              .findFirst()
              .map(Config::getValue)
              .map(JsonObject::new)
              .map(TlrSettingsConfiguration::from)
              .map(Future::succeededFuture)
              .orElseGet(() -> failedFuture("Failed to find TLR configuration"));
          } catch (JsonProcessingException e) {
            log.error("Failed to parse response: {}", response.bodyAsString());
            return failedFuture(e);
          }
        }
      });
  }
}
