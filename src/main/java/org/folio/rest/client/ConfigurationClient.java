package org.folio.rest.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static java.lang.String.format;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.configuration.TlrSettingsConfiguration;
import org.folio.support.exception.HttpException;
import org.folio.util.StringUtil;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

public class ConfigurationClient extends OkapiClient {
  private static final Logger log = LogManager.getLogger(ConfigurationClient.class);

  public ConfigurationClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<TlrSettingsConfiguration> getTlrSettings() {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    String query = cqlAnd(cqlExactMatch("module", "SETTINGS"),
      cqlExactMatch("configName", "TLR"));
    String url = format("/configurations/entries?query=%s", StringUtil.urlEncode(query));
    okapiGetAbs(url).send(promise);

    return promise.future().compose(response -> {
      int responseStatus = response.statusCode();
      if (responseStatus != 200) {
        String errorMessage = String.format("Failed to find TLR configuration. Response: %d %s",
          responseStatus, response.bodyAsString());
        log.error(errorMessage);
        return failedFuture(new HttpException(GET, url, response));
      } else {
        return succeededFuture(TlrSettingsConfiguration.from(response.bodyAsJsonObject()));
      }
    });
  }

  private String cqlExactMatch(String index, String value) {
    return format("%s==\"%s\"", index, value);
  }

  private String cqlAnd(String left, String right) {
    return format("%s and %s", left, right);
  }
}