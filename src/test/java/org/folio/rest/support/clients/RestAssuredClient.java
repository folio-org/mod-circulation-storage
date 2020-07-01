package org.folio.rest.support.clients;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;

import javax.ws.rs.core.MediaType;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class RestAssuredClient {
  private final OkapiHeaders okapiHeaders;
  private final RestAssuredConfig restAssuredConfig;

  public RestAssuredClient(OkapiHeaders okapiHeaders, RestAssuredConfig restAssuredConfig) {
    this.okapiHeaders = okapiHeaders;
    this.restAssuredConfig = restAssuredConfig;
  }

  public Response get(String uri) {
    return getRequestSpecification()
      .when()
      .get(uri)
      .then()
      .log().all()
      .extract().response();
  }

  public Response post(String uri, Object body) {
    return getRequestSpecification()
      .when()
      .body(body)
      .post(uri)
      .then()
      .log().all()
      .extract().response();
  }

  private RequestSpecification getRequestSpecification() {
    return RestAssured.given()
      .config(restAssuredConfig)
      .baseUri(okapiHeaders.getUrl())
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, okapiHeaders.getTenantId()))
      .header(new Header("x-okapi-url", okapiHeaders.getUrl()))
      .header(new Header(OKAPI_HEADER_TOKEN, okapiHeaders.getToken()));
  }
}
