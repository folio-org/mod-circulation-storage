package org.folio.rest.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.RequestType;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.server.UID;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class RequestPoliciesApiTest extends ApiTests {

  private static final int CONNECTION_TIMEOUT = 5;
  private static final String DEFAULT_REQUEST_POLICY_NAME = "default_request_policy";
/*
  @After
  public void CleanupAfterEachTest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    CompletableFuture<JsonResponse> deleteCompleted = new CompletableFuture<>();

    client.delete(requestPolicyStorageUrl(""),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(deleteCompleted));

    JsonResponse response = deleteCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("response is null", response != null);
  }
*/
  @Test
  public void canCreateARequestPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();
    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);

    RequestPolicy requestPolicy = new RequestPolicy();
    requestPolicy.withDescription("test policy");
    requestPolicy.withId(id.toString());
    requestPolicy.withName("successful_get");
    requestPolicy.withRequestTypes(requestTypes);

    client.post(requestPolicyStorageUrl(""),
                requestPolicy,
                StorageTestSuite.TENANT_ID,
                ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat("Failed to create new request-policy", response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("name"), is("successful_get"));
    assertThat(representation.getString("description"), is("test policy"));

    JsonArray resultRequestTypes = representation.getJsonArray("requestTypes");

    for ( Object type : resultRequestTypes) {
      assertThat("requestType returned: " + type.toString() + " does not exist in input list",
                        requestTypes.contains(RequestType.fromValue(type.toString())));
    }
  }

  @Test
  public void canCreateRequestPolicyWithoutUID()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    List<RequestType> requestTypes = Collections.singletonList(RequestType.HOLD);

    RequestPolicy requestPolicy = new RequestPolicy();
    requestPolicy.withDescription("test policy");
    requestPolicy.withName("successful_get");
    requestPolicy.withRequestTypes(requestTypes);

    client.post(requestPolicyStorageUrl(""),
      requestPolicy,
                StorageTestSuite.TENANT_ID,
                ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();
    assertThat(representation.getString("id"), is(notNullValue()));
    assertThat(representation.getString("name"), is("successful_get"));
    assertThat(representation.getString("description"), is("test policy"));
  }

  @Test
  public void cannotCreateRequestPolicyWithExistingName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException{
      CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
      CompletableFuture<JsonResponse> failedCompleted = new CompletableFuture<>();

      String reqPolicyName = "request_policy_name";

      List<RequestType> requestTypes = Collections.singletonList(RequestType.HOLD);
      UUID id = UUID.randomUUID();

      RequestPolicy requestPolicy = new RequestPolicy();
      requestPolicy.withDescription("test policy");
      requestPolicy.withName(reqPolicyName);
      requestPolicy.withRequestTypes(requestTypes);
      requestPolicy.withId(id.toString());

      client.post(requestPolicyStorageUrl(""),
        requestPolicy,
        StorageTestSuite.TENANT_ID,
        ResponseHandler.json(createCompleted));

      JsonResponse response = createCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
      assertThat(String.format("Failed to create request policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

      UUID id2 = UUID.randomUUID();
      RequestPolicy requestPolicy2 = new RequestPolicy();
      requestPolicy2.withDescription("test policy 2");
      requestPolicy2.withName(reqPolicyName);
      requestPolicy2.withRequestTypes(requestTypes);
      requestPolicy2.withId(id2.toString());

      client.post(requestPolicyStorageUrl(""),
        requestPolicy2,
        StorageTestSuite.TENANT_ID,
        ResponseHandler.json(failedCompleted));

        JsonResponse response2 = failedCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
        JsonObject representation = response2.getJson();

        assertThat(String.format("Failed to create request policy: %s", response2.getBody()),
          response2.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));

        JsonArray errors = representation.getJsonArray("errors");
        JsonObject error  = errors.getJsonObject(0);
        assertThat("unexpected error message" , error.getString("message").contains("duplicate key value"));
  }

  @Test
  public void cannotCreateRequestPolicyWithBadUID()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException{

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    List<RequestType> requestTypes = Collections.singletonList(RequestType.HOLD);

    RequestPolicy requestPolicy = new RequestPolicy();
    requestPolicy.withDescription("test policy");
    requestPolicy.withName("successful_get");
    requestPolicy.withRequestTypes(requestTypes);
    requestPolicy.withId("d9cdbed-1b49-4b5e-a7bd-064b8d231");

    client.post(requestPolicyStorageUrl(""),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject representation = response.getJson();

    JsonArray errors = representation.getJsonArray("errors");
    JsonObject error  = errors.getJsonObject(0);

    assertThat(String.format("Failed to create request policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
    assertThat("unexpected error message" , error.getString("message").contains("invalid input syntax for type uuid"));
  }

  @Test
  public void canGetRequestPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted1 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> createCompleted2 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> createCompletedGet = new CompletableFuture<>();

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);

    UUID id1 = UUID.randomUUID();
    RequestPolicy requestPolicy1 = new RequestPolicy();
    requestPolicy1.withDescription("test policy");
    requestPolicy1.withId(id1.toString());
    requestPolicy1.withName("successful_get1");
    requestPolicy1.withRequestTypes(requestTypes);

    UUID id2 = UUID.randomUUID();
    RequestPolicy requestPolicy2 = new RequestPolicy();
    requestPolicy2.withDescription("test policy");
    requestPolicy2.withId(id2.toString());
    requestPolicy2.withName("successful_get2");
    requestPolicy2.withRequestTypes(requestTypes);

    //Create requestPolicy1
    client.post(requestPolicyStorageUrl(""),
      requestPolicy1,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted1));

    JsonResponse response1 = createCompleted1.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat("Failed to create new request-policy", response1.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    //Create requestPolicy2
    client.post(requestPolicyStorageUrl(""),
      requestPolicy2,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted2));

    JsonResponse response2 = createCompleted2.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to create new request-policy", response2.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompletedGet));

    JsonResponse responseGet = createCompletedGet.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject getResultsJson = responseGet.getJson();

    JsonArray requestPolicies = getResultsJson.getJsonArray("requestPolicies");
    assertThat(getResultsJson.getInteger("totalRecords"), is(2));

    for (int i = 0; i < 2; i ++) {
      JsonObject aPolicy = requestPolicies.getJsonObject(i);

      if (i == 0) {
        assertThat(aPolicy.getString("id"), is(id1.toString()));
      }
      else {
        assertThat(aPolicy.getString("id"), is(id2.toString()));
      }

      assertThat(aPolicy.getString("name"), is("successful_get" +(i+1)));
      assertThat(aPolicy.getString("description"), is("test policy"));

      JsonArray resultRequestTypes = aPolicy.getJsonArray("requestTypes");

      for ( Object type : resultRequestTypes) {
        assertThat("requestType returned: " + type.toString() + " does not exist in input list",
          requestTypes.contains(RequestType.fromValue(type.toString())));
      }
    }
  }

  @Test
  public void canGetRequestPoliciesByIdUsingQuery()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCommpleted = new CompletableFuture<>();

    RequestPolicy requestPolicy1 = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("?query=id="+ requestPolicy1.getId()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCommpleted));

    JsonResponse responseGet = getCommpleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject responseJson = responseGet.getJson();

    JsonArray requestPolicies = responseJson.getJsonArray("requestPolicies");
    assertThat(responseJson.getInteger("totalRecords"), is(1));

    JsonObject aPolicy = requestPolicies.getJsonObject(0);

    assertThat(aPolicy.getString("id"), is(requestPolicy1.getId()));
    assertThat(aPolicy.getString("name"), is(requestPolicy1.getName()));
    assertThat(aPolicy.getString("description"), is(requestPolicy1.getDescription()));

    JsonArray resultRequestTypes = aPolicy.getJsonArray("requestTypes");

    for ( Object type : resultRequestTypes) {
      assertThat("requestType returned: " + type.toString() + " does not exist in input list",
        requestPolicy1.getRequestTypes().contains(RequestType.fromValue(type.toString())));
    }
  }

  @Test
  public void cannotGetRequestPoliciesByUsingInvalidOffsetAndLimit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCommpleted = new CompletableFuture<>();

    RequestPolicy requestPolicy = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("?query=(name=" + requestPolicy.getId() + ")&limit=-1&offset=-230"), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCommpleted));

    JsonResponse responseGet = getCommpleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to not get request-policy", responseGet.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
    JsonObject response = responseGet.getJson();
    JsonArray errors = response.getJsonArray("errors");
    JsonObject anError = errors.getJsonObject(0);

    assertThat("expected error message not found",anError.getString("message").contains("OFFSET must not be negative"));
  }

  @Test
  public void cannotGetRequestPoliciesByNonExistentName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCommpleted = new CompletableFuture<>();

    RequestPolicy requestPolicy = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("?query=name=" + requestPolicy.getName() + "blabblabla"), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCommpleted));

    JsonResponse responseGet = getCommpleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to not get request-policy", responseGet.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject getResultsJson = responseGet.getJson();
    assertThat(getResultsJson.getInteger("totalRecords"), is(0));
  }

  @Test
  public void canGetRequestPoliciesByName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted1 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> createCompleted2 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> getCommpleted = new CompletableFuture<>();

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);

    UUID id1 = UUID.randomUUID();
    RequestPolicy requestPolicy1 = new RequestPolicy();
    requestPolicy1.withDescription("test policy");
    requestPolicy1.withId(id1.toString());
    requestPolicy1.withName("successful_get1");
    requestPolicy1.withRequestTypes(requestTypes);

    UUID id2 = UUID.randomUUID();
    RequestPolicy requestPolicy2 = new RequestPolicy();
    requestPolicy2.withDescription("test policy");
    requestPolicy2.withId(id2.toString());
    requestPolicy2.withName("successful Get2");
    requestPolicy2.withRequestTypes(requestTypes);

    //Create requestPolicy1
    client.post(requestPolicyStorageUrl(""),
      requestPolicy1,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted1));

    JsonResponse response1 = createCompleted1.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat("Failed to create new request-policy", response1.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    //Create requestPolicy2
    client.post(requestPolicyStorageUrl(""),
      requestPolicy2,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted2));

    JsonResponse response2 = createCompleted2.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to create new request-policy", response2.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("?query=name=successful+Get2"), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCommpleted));

    JsonResponse responseGet = getCommpleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject getResultsJson = responseGet.getJson();

    JsonArray requestPolicies = getResultsJson.getJsonArray("requestPolicies");
    assertThat(getResultsJson.getInteger("totalRecords"), is(1));

    JsonObject aPolicy = requestPolicies.getJsonObject(0);
    assertThat(aPolicy.getString("id"), is(id2.toString()));
    assertThat(aPolicy.getString("name"), is("successful Get2"));
    assertThat(aPolicy.getString("description"), is("test policy"));

    JsonArray resultRequestTypes = aPolicy.getJsonArray("requestTypes");

    for ( Object type : resultRequestTypes) {
      assertThat("requestType returned: " + type.toString() + " does not exist in input list",
        requestTypes.contains(RequestType.fromValue(type.toString())));
    }
  }

  @Test
  public void canGetRequestPoliciesById()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCommpleted = new CompletableFuture<>();
    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.RECALL);

    createDefaultRequestPolicy();
    RequestPolicy req2 = createDefaultRequestPolicy(UUID.randomUUID(), "request2 name",
                              "request policy 2 descr", requestTypes );

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("/" + req2.getId()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCommpleted));

    JsonResponse responseGet = getCommpleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject aPolicy = responseGet.getJson();

    assertThat(aPolicy.getString("id"), is(req2.getId()));
    assertThat(aPolicy.getString("name"), is(req2.getName()));
    assertThat(aPolicy.getString("description"), is(req2.getDescription()));

    JsonArray resultRequestTypes = aPolicy.getJsonArray("requestTypes");

    for ( Object type : resultRequestTypes) {
      assertThat("requestType returned: " + type.toString() + " does not exist in input list",
        req2.getRequestTypes().contains(RequestType.fromValue(type.toString())));
    }
  }

  @Test
  public void cannotGetRequestPoliciesByNonexistingId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    createDefaultRequestPolicy();

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    UUID id = UUID.randomUUID();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("/" +id.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to not retrieve a request policy by non-existing ID", responseGet.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canDeleteRequestPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    //Get them to verify that they're in the system.
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> delCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> getCompletedVerify = new CompletableFuture<>();

    //create a couple of request policies to delete.
    createDefaultRequestPolicy();
    createDefaultRequestPolicy(UUID.randomUUID(), "test policy 2", "descr of test policy 2", null );

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl( ""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject getResultsJson = responseGet.getJson();

    assertThat(getResultsJson.getInteger("totalRecords"), is(2));

    //Delete all policies
    client.delete(requestPolicyStorageUrl( ""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(delCompleted));
    JsonResponse responseDel = delCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat("Failed to delete all request policies", responseDel.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    //Get all policies again to verify that none comes back
    client.get(requestPolicyStorageUrl( ""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompletedVerify));

    JsonResponse responseGetVerify = getCompletedVerify.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject getResultsVerfiyJson = responseGetVerify.getJson();

    assertThat(getResultsVerfiyJson.getInteger("totalRecords"), is(0));
  }

  @Test
  public void canDeleteRequestPoliciesById()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    //Get them to verify that they're in the system.
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> delCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> getCompletedVerify = new CompletableFuture<>();

    //create a couple of request policies to delete.
    RequestPolicy rp = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl( "/" + rp.getId()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject getResultsJson = responseGet.getJson();

    assertThat("response is null", getResultsJson != null);
    assertThat(getResultsJson.getString("id"), is(rp.getId()));

    //Delete existing policy
    client.delete(requestPolicyStorageUrl( "/" + rp.getId()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(delCompleted));
    JsonResponse responseDel = delCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat("Failed to delete all request policies", responseDel.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    //Get all policies again to verify that none comes back
    client.get(requestPolicyStorageUrl( "/" + rp.getId()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompletedVerify));

    JsonResponse responseGetVerify = getCompletedVerify.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to not get request-policy", responseGetVerify.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void cannotDeleteRequestPoliciesByNonExistingId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    //Get them to verify that they're in the system.
    CompletableFuture<JsonResponse> delCompleted = new CompletableFuture<>();

    //Delete existing policy
    client.delete(requestPolicyStorageUrl( "/" + UUID.randomUUID().toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(delCompleted));
    JsonResponse responseDel = delCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat("Failed to not delete all request policies", responseDel.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void canUpdateRequestPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);
    RequestPolicy requestPolicy = createDefaultRequestPolicy(UUID.randomUUID(), "sample requet policy",
                                                            "plain description", requestTypes);
    //update requestPolicy to new values
    requestPolicy.setDescription("new description");
    requestPolicy.setName("sample request policies");
    requestPolicy.setRequestTypes(Arrays.asList( RequestType.RECALL, RequestType.HOLD));

    client.put(requestPolicyStorageUrl("/" + requestPolicy.getId()),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    JsonResponse response = updateCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to update request-policy", response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    //Get it and examine the updated content
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(requestPolicyStorageUrl("/" + requestPolicy.getId()),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to update request-policy", getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject aPolicy = getResponse.getJson();

    assertThat(aPolicy.getString("id"), is(requestPolicy.getId()));
    assertThat(aPolicy.getString("name"), is(requestPolicy.getName()));
    assertThat(aPolicy.getString("description"), is(requestPolicy.getDescription()));

    JsonArray resultRequestTypes = aPolicy.getJsonArray("requestTypes");

    for ( Object type : resultRequestTypes) {
      assertThat("requestType returned: " + type.toString() + " does not exist in input list",
        requestPolicy.getRequestTypes().contains(RequestType.fromValue(type.toString())));
    }
  }

  @Test
  public void cannotUpdateRequestPolicyWithWrongId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);
    RequestPolicy requestPolicy = createDefaultRequestPolicy(UUID.randomUUID(), "sample request policy",
      "plain description", requestTypes);
    //update
    String newUid = UUID.randomUUID().toString();
    requestPolicy.setDescription("new description");

    client.put(requestPolicyStorageUrl("/" + newUid),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    //Because of the unique name constraint, instead of getting an error message about a nonexistent object, the message
    //is about duplicate name. This happens because PUT can also be used to add a new record to the database.
    JsonResponse response = updateCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject anError = extractErrorObject(response);
    assertThat("Failed to update request-policy", response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
    assertThat("Error message does not contain keyword 'already existed'", anError.getString("message").contains("already exists"));
  }

  @Test
  public void cannotUpdateRequestPolicyToExistingPolicyName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();
    String policy1Name = "Policy 1";
    String policy2Name = "Policy 2";

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);
    createDefaultRequestPolicy(UUID.randomUUID(), policy1Name,
      "plain description", requestTypes);

    RequestPolicy requestPolicy2 = createDefaultRequestPolicy(UUID.randomUUID(), policy2Name,
      "plain description", requestTypes);

    //update: set the name of requestPolicy2 to be of policy1's.
    requestPolicy2.setDescription("new description for policy 2");
    requestPolicy2.setName(policy1Name);
    requestPolicy2.setRequestTypes(Arrays.asList( RequestType.RECALL, RequestType.HOLD));

    client.put(requestPolicyStorageUrl("/" + requestPolicy2.getId()),
      requestPolicy2,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    JsonResponse response = updateCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to update request-policy", response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));

    JsonObject anError = extractErrorObject(response);

    assertThat("Error message does not contain keyword 'already existed'", anError.getString("message").contains("already exists"));
  }

  @Test
  public void canUpdateRequestPolicyWithNewId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);
    RequestPolicy requestPolicy = createDefaultRequestPolicy(UUID.randomUUID(), "old name",
      "plain description", requestTypes);

    //update requestPolicy
    String newUid = UUID.randomUUID().toString();
    requestPolicy.setDescription("new description");
    requestPolicy.setName("new name");
    requestPolicy.setId(newUid);

    client.put(requestPolicyStorageUrl("/" + newUid),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    JsonResponse response = updateCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to update request-policy", response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotUpdateRequestPolicyToBadId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();
    String policy1Name = "select count(*)";
    String badId = "select count(*)";

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);
    RequestPolicy requestPolicy = createDefaultRequestPolicy(UUID.randomUUID(), policy1Name,
      "plain description", requestTypes);

    //update set RequestPolicy's Id to nonexistent.
    requestPolicy.setId(badId);
    requestPolicy.setDescription("new description for policy 1");
    requestPolicy.setRequestTypes(Arrays.asList( RequestType.RECALL, RequestType.HOLD));

    client.put(requestPolicyStorageUrl("/" + requestPolicy.getId()),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    JsonResponse response = updateCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to update request-policy", response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));

    JsonObject anError = extractErrorObject(response);

    assertThat("Error message does not contain keyword 'already existed'", anError.getString("message").contains("already exists"));
  }

  private URL requestPolicyStorageUrl(String path) throws MalformedURLException {
    String completePath = "/request-policy-storage/request-policies" + path;
    return StorageTestSuite.storageUrl(completePath);
  }

  private RequestPolicy createDefaultRequestPolicy()  throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);
    UUID id1 = UUID.randomUUID();

    return createDefaultRequestPolicy(id1, DEFAULT_REQUEST_POLICY_NAME, "test policy", requestTypes);
  }

  private RequestPolicy createDefaultRequestPolicy(UUID id, String name, String descr, List<RequestType> requestTypes)  throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCommpleted = new CompletableFuture<>();

    RequestPolicy requestPolicy = new RequestPolicy();
    requestPolicy.withDescription(descr);
    requestPolicy.withId(id.toString());
    requestPolicy.withName(name);
    requestPolicy.withRequestTypes(requestTypes);

    //Create requestPolicy
    client.post(requestPolicyStorageUrl(""),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCommpleted));

    JsonResponse response = createCommpleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to create new request-policy", response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return  requestPolicy;
  }

  private JsonObject extractErrorObject(JsonResponse response){
    JsonObject responseJson = response.getJson();
    JsonArray errors = responseJson.getJsonArray("errors");
    JsonObject anError = errors.getJsonObject(0);

    return anError;
  }

}
