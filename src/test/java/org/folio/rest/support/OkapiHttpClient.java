package org.folio.rest.support;

import io.vertx.core.http.HttpMethod;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

public class OkapiHttpClient {

  private static final Logger log = LogManager.getLogger();

  private static final String TENANT_HEADER = "X-Okapi-Tenant";
  private static final String USERID_HEADER = "X-Okapi-User-Id";

  private io.vertx.ext.web.client.WebClient client;

  private final String defaultUserId = UUID.randomUUID().toString();

  public OkapiHttpClient(Vertx vertx) {
    client = io.vertx.ext.web.client.WebClient.create(vertx);
  }

  private void stdHeaders(HttpRequest<Buffer> request, URL url, String tenantId, String userId) {
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
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler) {

    post(url, body, tenantId, defaultUserId, responseHandler);
  }

  public void post(URL url,
    Object body,
    String tenantId,
    String userId,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler) {

    HttpRequest<Buffer> request = client.requestAbs(HttpMethod.POST, url.toString());

    request.headers().add("Accept", "application/json, text/plain");
    request.headers().add("Content-type", "application/json");

    stdHeaders(request, url, tenantId, userId);

    if (body == null) {
      request.send(responseHandler);
      return;
    }

    Buffer encodedBody = Buffer.buffer(Json.encodePrettily(body));
    log.info(String.format("POST %s, Request: %s",
      url.toString(), body));
    request.sendBuffer(encodedBody, responseHandler);
  }

  public void post(URL url,
    String tenantId,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler) {

    post(url, null, tenantId, responseHandler);
  }

  public void get(URL url,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler)
    throws UnsupportedEncodingException {

    get(url, null, responseHandler);
  }

  public void put(URL url,
    Object body,
    String tenantId,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler) {

    put(url, body, tenantId, defaultUserId, responseHandler);
  }

  public void put(URL url,
    Object body,
    String tenantId,
    String userId,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler) {

    HttpRequest<Buffer> request = client.requestAbs(HttpMethod.PUT, url.toString());

    request.headers().add("Accept", "application/json, text/plain");
    request.headers().add("Content-type", "application/json");

    stdHeaders(request, url, tenantId, userId);

    Buffer encodedBody = Buffer.buffer(Json.encodePrettily(body));
    log.info(String.format("PUT %s, Request: %s",
      url.toString(), body));
    request.sendBuffer(encodedBody, responseHandler);
  }

  public void get(URL url,
    String tenantId,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler) {

    get(url.toString(), tenantId, responseHandler);
  }

  public void get(URL url,
    String query,
    String tenantId,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler)
    throws MalformedURLException {

    get(new URL(url.getProtocol(), url.getHost(), url.getPort(),
      url.getPath() + "?" + query),
      tenantId, responseHandler);
  }

  public void get(String url,
    String tenantId,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler) {

    HttpRequest<Buffer> request = client.requestAbs(HttpMethod.GET, url);

    request.headers().add("Accept", "application/json");

    stdHeaders(request, null, tenantId, defaultUserId);
    request.send(responseHandler);
  }

  public void delete(URL url,
    String tenantId,
    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler) {

    HttpRequest<Buffer> request = client.requestAbs(HttpMethod.DELETE, url.toString());

    request.headers().add("Accept", "application/json, text/plain");

    stdHeaders(request, url, tenantId, defaultUserId);
    request.send(responseHandler);
  }

}
