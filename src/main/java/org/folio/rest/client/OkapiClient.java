package org.folio.rest.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.util.StringUtil.urlEncode;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class OkapiClient {
  protected static final Logger log = LoggerFactory.getLogger(OkapiClient.class);
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  protected static final ObjectMapper objectMapper = new ObjectMapper();

  private final WebClient webClient;
  private final String okapiUrl;
  private final String tenant;
  private final String token;

  public OkapiClient(Vertx vertx, Map<String, String> okapiHeaders) {
    this.webClient = WebClientProvider.getWebClient(vertx);
    okapiUrl = okapiHeaders.get(OKAPI_URL_HEADER);
    tenant = okapiHeaders.get(OKAPI_HEADER_TENANT);
    token = okapiHeaders.get(OKAPI_HEADER_TOKEN);
  }

  HttpRequest<Buffer> okapiGetAbs(String path) {
    return webClient.getAbs(okapiUrl + path)
      .putHeader(OKAPI_HEADER_TENANT, tenant)
      .putHeader(OKAPI_URL_HEADER, okapiUrl)
      .putHeader(OKAPI_HEADER_TOKEN, token)
      .putHeader(ACCEPT, APPLICATION_JSON);
  }

  HttpRequest<Buffer> okapiPostAbs(String path) {
    return webClient.postAbs(okapiUrl + path)
      .putHeader(ACCEPT, APPLICATION_JSON)
      .putHeader(OKAPI_HEADER_TENANT, tenant)
      .putHeader(OKAPI_URL_HEADER, okapiUrl)
      .putHeader(OKAPI_HEADER_TOKEN, token);
  }

  public <T> Future<Collection<T>> get(String resourcePath, Collection<String> ids,
    String collectionName, Class<T> objectType) {

    String query = ids.stream()
      .distinct()
      .map(id -> "id==" + id)
      .collect(joining(" OR "));

    return getAsJson(resourcePath, query, ids.size())
      .map(responseJson -> responseJson.getJsonArray(collectionName)
        .stream()
        .map(JsonObject.class::cast)
        .map(json -> json.mapTo(objectType))
        .collect(toList())
      );
  }

  public Future<JsonObject> getAsJson(String resourcePath, String query, int limit) {
    return get(resourcePath, query, limit)
      .map(HttpResponse::bodyAsJsonObject);
  }

  public Future<HttpResponse<Buffer>> get(String resourcePath, String query, int limit) {
    return get(String.format("%s?limit=%d&query=%s", resourcePath, limit, urlEncode(query)));
  }

  private Future<HttpResponse<Buffer>> get(String url) {
    log.debug("Calling GET {}", url);

    return okapiGetAbs(url).send()
      .compose(response -> {
        if (response.statusCode() == 200) {
          return succeededFuture(response);
        }
        final String errorMessage = format("Request failed: GET %s. Response: [%s] %s",
          url, response.statusCode(), response.bodyAsString());
        log.error(errorMessage);
        return failedFuture(errorMessage);
      });
  }
}
