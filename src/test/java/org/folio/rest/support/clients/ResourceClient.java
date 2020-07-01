package org.folio.rest.support.clients;

import java.util.List;

import org.folio.rest.support.JsonResponse;

import io.restassured.response.Response;

public final class ResourceClient<T> {
  private final String baseUri;
  private final String collectionProperty;
  private final RestAssuredClient restAssuredClient;
  private final Class<T> type;

  public ResourceClient(String baseUri, String collectionProperty,
    RestAssuredClient restAssuredClient, Class<T> targetClass) {

    this.baseUri = baseUri;
    this.collectionProperty = collectionProperty;
    this.restAssuredClient = restAssuredClient;
    this.type = targetClass;
  }

  public JsonResponse create(T body) {
    final Response response = restAssuredClient
      .post(baseUri, body)
      .then().statusCode(201)
      .extract().response();

    return new JsonResponse(response.statusCode(), response.body().print());
  }

  public List<T> getMany(CqlQuery cqlQuery) {
    final String queryParams = "?query=" + cqlQuery.asString()
      + "&limit=" + Integer.MAX_VALUE + "&offset=0";

    return restAssuredClient.get(baseUri + queryParams)
      .then().statusCode(200)
      .extract()
      .jsonPath().getList(collectionProperty, type);
  }
}
