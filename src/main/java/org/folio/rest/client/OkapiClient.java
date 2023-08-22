package org.folio.rest.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static java.lang.System.currentTimeMillis;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.support.AsyncUtils.mapSequentially;
import static org.folio.util.StringUtil.urlEncode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.support.exception.HttpException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class OkapiClient {
  protected static final Logger log = LogManager.getLogger(OkapiClient.class);
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final int ID_BATCH_SIZE = 88;
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

  Future<HttpResponse<Buffer>> okapiGet(String path) {
    log.debug("okapiGet:: path: {}", path);

    try {
      return webClient.getAbs(okapiUrl + path)
        .putHeader(OKAPI_HEADER_TENANT, tenant)
        .putHeader(OKAPI_URL_HEADER, okapiUrl)
        .putHeader(OKAPI_HEADER_TOKEN, token)
        .putHeader(ACCEPT, APPLICATION_JSON)
        .send()
        .onSuccess(r -> log.debug("okapiGet:: GET request success: {}{}", okapiUrl, path))
        .onFailure(t -> log.warn("okapiGet:: GET request failed: {}{}", okapiUrl, path, t));
    } catch (Exception e) {
      log.warn("okapiGet:: GET request failed: {}{}", okapiUrl, path, e);
      return Future.failedFuture(e);
    }
  }

  public <T> Future<Collection<T>> get(String path, Collection<String> ids, String collectionName,
    Class<T> objectType) {

    if (log.isDebugEnabled()) {
      log.debug("get:: path: {}, ids: {} items, objectType: {}, collectionName: {}",
        path, ids.size(), collectionName, objectType.getSimpleName());
    }

    Set<String> filteredIds = ids.stream()
      .filter(StringUtils::isNotBlank)
      .collect(toSet());

    if (filteredIds.isEmpty()) {
      log.info("get:: no IDs to fetch");
      return succeededFuture(new ArrayList<>());
    }

    log.info("get:: fetching {} {} by IDs", ids.size(), objectType.getSimpleName());
    List<List<String>> batches = ListUtils.partition(new ArrayList<>(filteredIds), ID_BATCH_SIZE);

    return mapSequentially(batches, batch -> fetchBatch(path, batch, objectType, collectionName))
      .map(results -> results.stream().flatMap(Collection::stream).toList());
  }

  private <T> Future<Collection<T>> fetchBatch(String resourcePath, List<String> batch,
    Class<T> objectType, String collectionName) {

    if (log.isDebugEnabled()) {
      log.debug("fetchBatch:: fetching batch of {} {}", batch.size(), objectType.getSimpleName());
    }

    String query = String.format("id==(%s)", String.join(" or ", batch));

    return get(resourcePath, query, objectType, collectionName, batch.size());
  }

  public <T> Future<Collection<T>> get(String resourcePath, String query, Class<T> objectType,
    String collectionName, int limit) {

    if (log.isDebugEnabled()) {
      log.debug("get:: resourcePath: {}, query: {}, objectType: {}, collectionName: {}, limit: {}",
        resourcePath, query, objectType.getSimpleName(), collectionName, limit);
    }

    String path = String.format("%s?query=%s&limit=%d", resourcePath, urlEncode(query), limit);

    final long startTimeMillis = log.isDebugEnabled() ? -1L : currentTimeMillis();

    return okapiGet(path)
      .compose(response -> {
        int responseStatus = response.statusCode();
        if (log.isDebugEnabled()) {
          log.debug("get:: [{}] [{} ms] GET {}", responseStatus,
            currentTimeMillis() - startTimeMillis, resourcePath);
        }
        if (responseStatus != 200) {
          HttpException exception = new HttpException(GET, path, response);
          log.warn("get:: GET by query failed: {}", path, exception);
          return failedFuture(exception);
        }
        return succeededFuture(
          response.bodyAsJsonObject()
            .getJsonArray(collectionName)
            .stream()
            .map(JsonObject::mapFrom)
            .map(json -> json.mapTo(objectType))
            .collect(toList())
        );
      });
  }
}
