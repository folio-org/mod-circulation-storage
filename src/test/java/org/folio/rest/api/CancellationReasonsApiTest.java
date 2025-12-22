package org.folio.rest.api;

import io.vertx.core.json.JsonObject;

import org.folio.rest.support.*;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.hamcrest.MatcherAssert;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesBadRequest;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesCreated;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesNoContent;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesNotFound;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesOk;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesUnprocessableEntity;
import static org.folio.rest.api.RequestsApiTest.requestStorageUrl;
import static org.folio.rest.support.builders.RequestRequestBuilder.CLOSED_CANCELLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author kurt
 */
public class CancellationReasonsApiTest extends ApiTests {

  private static URL cancelReasonURL() throws MalformedURLException {
    return cancelReasonURL("");
  }

  private static URL cancelReasonURL(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl(
      "/cancellation-reason-storage/cancellation-reasons" + subPath);
  }

  private void assertCreateCancellationReason(JsonObject request)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    JsonResponse response = createCancellationReason(request);

    MatcherAssert.assertThat(String.format("Failed to create cancellation reason: %s",
        response.getBody()), response, matchesCreated());

    new IndividualResource(response);
  }

  private JsonResponse createCancellationReason(JsonObject request)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(cancelReasonURL(), request, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  private IndividualResource assertGetCancellationReason(String id)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    JsonResponse response = getCancellationReason(id);

    MatcherAssert.assertThat(String.format("Failed to retrieve cancellation reason: %s (%s)",
        response.getBody(), response.getStatusCode()),
        response, matchesOk());

    return new IndividualResource(response);
  }

  private JsonResponse getCancellationReason(String id)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    CompletableFuture<JsonResponse> getReasonFuture = new CompletableFuture<>();
    client.get(cancelReasonURL("/"+id), StorageTestSuite.TENANT_ID,
        ResponseHandler.json(getReasonFuture));

    return getReasonFuture.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse getCancellationReasonCollection(String query)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    CompletableFuture<JsonResponse> getReasonsFuture = new CompletableFuture<>();
    String queryParam;
    if(query != null) {
      queryParam = "?query=" + query;
    } else {
      queryParam = "";
    }
    client.get(cancelReasonURL(queryParam), StorageTestSuite.TENANT_ID,
        ResponseHandler.json(getReasonsFuture));

    return getReasonsFuture.get(5, TimeUnit.SECONDS);
  }

  private TextResponse updateCancellationReason(String id, JsonObject request)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    CompletableFuture<TextResponse> updateReasonFuture = new CompletableFuture<>();
    client.put(cancelReasonURL("/"+id), request, StorageTestSuite.TENANT_ID,
        ResponseHandler.text(updateReasonFuture));

    return updateReasonFuture.get(5, TimeUnit.SECONDS);
  }

  private void assertUpdateCancellationReason(String id, JsonObject request)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    TextResponse response = updateCancellationReason(id, request);

    MatcherAssert.assertThat(String.format("Failed to update cancellation reason: %s (%s)",
        response.getBody(), response.getStatusCode()),
        response, matchesNoContent());
  }

  private TextResponse deleteCancellationReason(String id)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    CompletableFuture<TextResponse> deleteReasonFuture = new CompletableFuture<>();

    client.delete(cancelReasonURL("/"+id), StorageTestSuite.TENANT_ID,
        ResponseHandler.text(deleteReasonFuture));

    return deleteReasonFuture.get(5, TimeUnit.SECONDS);
  }

  private void assertDeleteCancellationReason(String id)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    TextResponse response = deleteCancellationReason(id);

    MatcherAssert.assertThat(String.format("Failed to delete cancellation reason: %s (%s)",
        response.getBody(), response.getStatusCode()),
        response, matchesNoContent());
  }
  //Test Init
  @BeforeEach
  public void beforeEach()
      throws MalformedURLException {
    StorageTestSuite.deleteAll(requestStorageUrl());
    StorageTestSuite.deleteAll(cancelReasonURL());
  }
  //Tests
  @Test
  public void canCreateCancellationReason()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    JsonObject request = new JsonObject()
        .put("name", "cosmicrays")
        .put("description", "Excess solar radiation has destroyed the item")
        .put("publicDescription", "The item has been destroyed")
        .put("source", "System");
    assertCreateCancellationReason(request);
  }

  @Test
  public void canCreateAndRetrieveCancellationRequest()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    String id = UUID.randomUUID().toString();
    JsonObject request = new JsonObject()
        .put("name", "slime")
        .put("id", id)
        .put("description", "Item slimed")
        .put("requiresAdditionalInformation", true)
        .put("source", "System");
    assertCreateCancellationReason(request);
    IndividualResource reason = assertGetCancellationReason(id);
    assertEquals("slime", reason.getJson().getString("name"));
    assertEquals("System", reason.getJson().getString("source"));
  }

  @Test
  public void canUpdateCancellationRequest()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    String id = UUID.randomUUID().toString();
    JsonObject request = new JsonObject()
        .put("name", "slime")
        .put("id", id)
        .put("description", "Item slimed");
    assertCreateCancellationReason(request);

    request
      .put("name", "oobleck")
      .put("requiresAdditionalInformation", false);

    assertUpdateCancellationReason(id, request);
    IndividualResource reason = assertGetCancellationReason(id);
    assertEquals("oobleck", reason.getJson().getString("name"));
  }

  @Test
  public void canRetrieveByCQL()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    JsonObject request = new JsonObject()
        .put("name", "ooze")
        .put("description", "Item oozed");
    JsonObject request2 = new JsonObject()
        .put("name", "fire")
        .put("description", "Item burnt");
    assertCreateCancellationReason(request);
    assertCreateCancellationReason(request2);
    JsonResponse response = getCancellationReasonCollection("description=burnt");
    assertTrue(response.getJson().containsKey("totalRecords"));
    assertTrue(response.getJson().getInteger("totalRecords").equals(1));
    assertEquals("fire", response.getJson().getJsonArray("cancellationReasons")
        .getJsonObject(0).getString("name"));
  }

  @Test
  public void canDeleteCancellationRequest()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    String id = UUID.randomUUID().toString();
    JsonObject request = new JsonObject()
        .put("name", "meteor")
        .put("id", id)
        .put("description", "SMOD incoming, all requests cancelled");
    assertCreateCancellationReason(request);
    assertDeleteCancellationReason(id);
    JsonResponse getResponse = getCancellationReason(id);
    assertThat(getResponse, matchesNotFound());
  }

  @Test
  public void cannotCreateDuplicateCancellationRequestNames()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    JsonObject request = new JsonObject()
        .put("name", "chicken")
        .put("description", "Giant chicken has eaten the item");
    JsonObject request2 = new JsonObject()
        .put("name", "chicken")
        .put("description", "Chicken grease stains on item");
    assertCreateCancellationReason(request);
    JsonResponse response = createCancellationReason(request2);
    assertThat(response, matchesUnprocessableEntity());
  }

  @Test
  void cannotDeleteCancellationReasonInUse()
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    UUID requestId = UUID.randomUUID();
    UUID cancellationReasonId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID proxyId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = new DateTime(2017, 7, 30, 0, 0, DateTimeZone.UTC);
    DateTime holdShelfExpirationDate = new DateTime(2017, 8, 31, 0, 0, DateTimeZone.UTC);

    JsonObject reasonRequest = new JsonObject()
        .put("name", "worms").put("description", "Item Eaten by space worms.")
        .put("id", cancellationReasonId.toString());

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .toHoldShelf()
      .withId(requestId)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .withProxyId(proxyId)
      .withRequestExpirationDate(requestExpirationDate)
      .withHoldShelfExpirationDate(holdShelfExpirationDate)
      .withItem("Nod", "565578437802")
      .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
      .withProxy("Stuart", "Rebecca", "6059539205")
      .withStatus(CLOSED_CANCELLED)
      .withCancellationReasonId(cancellationReasonId)
      .create();

    assertCreateCancellationReason(reasonRequest);
    CompletableFuture<JsonResponse> createRequestFuture = new CompletableFuture<>();
    client.post(requestStorageUrl(), requestRequest, StorageTestSuite.TENANT_ID,
        ResponseHandler.json(createRequestFuture));
    JsonResponse createRequestResponse = createRequestFuture.get(5, TimeUnit.SECONDS);
    assertThat(createRequestResponse, matchesCreated());
    TextResponse deleteReasonResponse = deleteCancellationReason(cancellationReasonId.toString());
    assertThat(deleteReasonResponse, matchesBadRequest());

  }
  @Test
  //My canary in the coalmine :)
  void dummyTest() {
    assertTrue(true);
  }


}
