package org.folio.rest.support;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpClient {

  private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

  private static final String TENANT_HEADER = "X-Okapi-Tenant";
  private static final String USERID_HEADER = "X-Okapi-User-Id";

  private final io.vertx.core.http.HttpClient client;

  private final String defaultUserId = UUID.randomUUID().toString();

  public HttpClient(Vertx vertx) {
    client = vertx.createHttpClient();
  }

  private void stdHeaders(HttpClientRequest request, URL url, String tenantId, String userId) {
    if (url != null) {
      request.headers().add("X-Okapi-Url", url.getProtocol() + "://" + url.getHost() + ":" + url.getPort());
      request.headers().add("X-Okapi-Url-to", url.getProtocol() + "://" + url.getHost() + ":" + url.getPort());
    }
    if (tenantId != null) {
      request.headers().add(TENANT_HEADER, tenantId);
    }
    if (userId != null) {
      request.headers().add(USERID_HEADER, userId);
    }
  }

  public void post(URL url,
    Object body,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    post(url, body, tenantId, defaultUserId, responseHandler);
  }

  public void post(URL url,
    Object body,
    String tenantId,
    String userId,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.postAbs(url.toString(),
      responseHandler);

    request.headers().add("Accept", "application/json, text/plain");
    request.headers().add("Content-type", "application/json");

    stdHeaders(request, url, tenantId, userId);
    if (body != null) {
      String encodedBody = Json.encodePrettily(body);
      log.info(String.format("POST %s, Request: %s",
        url.toString(), encodedBody));
      request.end(encodedBody);
    } else {
      request.end();
    }
  }

  public void post(URL url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    post(url, null, tenantId, responseHandler);
  }

  public void get(URL url,
    Handler<HttpClientResponse> responseHandler)
    throws UnsupportedEncodingException {

    get(url, null, responseHandler);
  }

  public void put(URL url,
    Object body,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    put(url, body, tenantId, defaultUserId, responseHandler);
  }

  public void put(URL url,
    Object body,
    String tenantId,
    String userId,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.putAbs(url.toString(), responseHandler);

    request.headers().add("Accept", "application/json, text/plain");
    request.headers().add("Content-type", "application/json");

    stdHeaders(request, url, tenantId, userId);
    String encodedBody = Json.encodePrettily(body);
    log.info(String.format("PUT %s, Request: %s",
      url.toString(), encodedBody));

    request.end(encodedBody);
  }

  public void get(URL url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    get(url.toString(), tenantId, responseHandler);
  }

  public void get(URL url,
    String query,
    String tenantId,
    Handler<HttpClientResponse> responseHandler)
    throws MalformedURLException {

    get(new URL(url.getProtocol(), url.getHost(), url.getPort(),
      url.getPath() + "?" + query),
      tenantId, responseHandler);
  }

  public void get(String url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.getAbs(url, responseHandler);

    request.headers().add("Accept", "application/json");

    stdHeaders(request, null, tenantId, defaultUserId);
    request.end();
  }

  public void delete(URL url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    delete(url.toString(), tenantId, responseHandler);
  }

  public void delete(String url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.deleteAbs(url, responseHandler);

    request.headers().add("Accept", "application/json, text/plain");

    stdHeaders(request, null, tenantId, defaultUserId);
    request.end();
  }
}
