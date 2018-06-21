/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.folio.rest.api.FixedDueDateApiTest.dueDateURL;
import static org.folio.rest.api.LoanPoliciesApiTest.loanPolicyStorageUrl;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import static org.hamcrest.core.Is.is;
import org.hamcrest.junit.MatcherAssert;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.folio.rest.api.FixedDueDateApiTest.dueDateURL;
import static org.folio.rest.api.LoanPoliciesApiTest.loanPolicyStorageUrl;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.folio.rest.api.FixedDueDateApiTest.dueDateURL;
import static org.folio.rest.api.LoanPoliciesApiTest.loanPolicyStorageUrl;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.folio.rest.api.FixedDueDateApiTest.dueDateURL;
import static org.folio.rest.api.LoanPoliciesApiTest.loanPolicyStorageUrl;
import org.folio.rest.support.Response;
import org.folio.rest.support.TextResponse;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author kurt
 */
public class CancellationReasonsApiTest extends ApiTests {
  @Before
  public void beforeEach()
    throws MalformedURLException {
    StorageTestSuite.deleteAll(cancelReasonURL());
  }
  
  protected static URL cancelReasonURL() throws MalformedURLException {
    return cancelReasonURL("");
  }

  protected static URL cancelReasonURL(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl(
      "/cancellation-reason-storage/cancellation-reasons" + subPath);
  }
  
  private IndividualResource assertCreateCancellationReason(JsonObject request)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {

    JsonResponse response = createCancellationReason(request);
    
    MatcherAssert.assertThat(String.format("Failed to create cancellation reason: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }
  
  private JsonResponse createCancellationReason(JsonObject request) 
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(cancelReasonURL(), request, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);
    return response;
  }
  
  private IndividualResource assertGetCancellationReason(String id) 
      throws MalformedURLException,
      InterruptedException, 
      ExecutionException,
      TimeoutException {    
    JsonResponse response = getCancellationReason(id);
    
    MatcherAssert.assertThat(String.format("Failed to retrieve cancellation reason: %s (%s)",
        response.getBody(), response.getStatusCode()),
        response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    
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
    JsonResponse response = getReasonFuture.get(5, TimeUnit.SECONDS);
    return response;
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
    JsonResponse response = getReasonsFuture.get(5, TimeUnit.SECONDS);
    return response;
  }
  
  private TextResponse updateCancellationReason(String id, JsonObject request) 
      throws MalformedURLException,
      InterruptedException,
      ExecutionException, 
      TimeoutException {
    CompletableFuture<TextResponse> updateReasonFuture = new CompletableFuture<>();
    client.put(cancelReasonURL("/"+id), request, StorageTestSuite.TENANT_ID,
        ResponseHandler.text(updateReasonFuture));
    TextResponse textResponse = updateReasonFuture.get(5, TimeUnit.SECONDS);
    return textResponse;    
  }
  
  private void assertUpdateCancellationReason(String id, JsonObject request)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException, 
      TimeoutException {
    TextResponse response = updateCancellationReason(id, request);
    
    MatcherAssert.assertThat(String.format("Failed to update cancellation reason: %s (%s)",
        response.getBody(), response.getStatusCode()),
        response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }
  
  private TextResponse deleteCancellationReason(String id)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException, 
      TimeoutException {
    CompletableFuture<TextResponse> deleteReasonFuture = new CompletableFuture<>();
    client.delete(cancelReasonURL("/"+id), StorageTestSuite.TENANT_ID,
        ResponseHandler.text(deleteReasonFuture));
    TextResponse textResponse = deleteReasonFuture.get(5, TimeUnit.SECONDS);
    return textResponse;
  }
  
  private void assertDeleteCancellationReason(String id)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException, 
      TimeoutException {
    TextResponse response = deleteCancellationReason(id);
    
    MatcherAssert.assertThat(String.format("Failed to delete cancellation reason: %s (%s)",
        response.getBody(), response.getStatusCode()),
        response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
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
        .put("description", "Excess solar radiation has destroyed the item");
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
        .put("description", "Item slimed");
    assertCreateCancellationReason(request);
    IndividualResource reason = assertGetCancellationReason(id);
    assertEquals(reason.getJson().getString("name"), "slime");
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
    request.put("name", "oobleck");
    assertUpdateCancellationReason(id, request);
    IndividualResource reason = assertGetCancellationReason(id);
    assertEquals(reason.getJson().getString("name"), "oobleck");
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
    JsonResponse response = getCancellationReasonCollection("description=*burnt");
    assertTrue(response.getJson().containsKey("totalRecords"));
    assertTrue(response.getJson().getInteger("totalRecords").equals(1));
    assertEquals(response.getJson().getJsonArray("cancellationReasons")
        .getJsonObject(0).getString("name"), "fire");
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
    assertTrue(getResponse.getStatusCode() == 404);
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
    assertEquals(400, response.getStatusCode());    
  }
  
  @Test
  //My canary in the coalmine :)
  public void dummyTest() {
    assertTrue(true);
  }
  
  
}
