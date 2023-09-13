package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.vertx.core.json.JsonObject.mapFrom;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isBadRequest;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isCreated;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNotFound;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isUnprocessableEntity;
import static org.folio.rest.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasCode;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.rest.support.matchers.ValidationResponseMatchers.isValidationResponseWhich;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.StringContains.containsString;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.AllowedServicePoints;
import org.folio.rest.jaxrs.model.RequestPolicy;
import org.folio.rest.jaxrs.model.RequestType;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.model.Servicepoints;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class RequestPoliciesApiTest extends ApiTests {

  private static final int CONNECTION_TIMEOUT = 5;
  private static final String DEFAULT_REQUEST_POLICY_NAME = "default_request_policy";
  private static final String SERVICE_POINTS_URL = "/service-points";
  private static final String INVALID_ALLOWED_SERVICE_POINT_ERROR_CODE = "INVALID_ALLOWED_SERVICE_POINT";
  private static int REQ_POLICY_NAME_INCR = 0;  //This number is appended to the name of the default request policy to ensure uniqueness

  @Before
  public void beforeEach() {
    truncateTable("request_policy");
  }

  @Test
  public void canCreateARequestPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    createDefaultRequestPolicy();
  }

  @Test
  public void canCreateRequestPolicyWithoutUID()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    List<RequestType> requestTypes = Collections.singletonList(RequestType.HOLD);
    createDefaultRequestPolicy(null,"successful_get","test policy",requestTypes);
  }

  @Test
  public void cannotCreateRequestPolicyWithoutName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> failedCompleted = new CompletableFuture<>();
    List<RequestType> requestTypes = Collections.singletonList(RequestType.HOLD);

    //create requestPolicy without name
    UUID id = UUID.randomUUID();
    RequestPolicy requestPolicy = new RequestPolicy();
    requestPolicy.withDescription("test policy 2");
    requestPolicy.withRequestTypes(requestTypes);
    requestPolicy.withId(id.toString());

    client.post(requestPolicyStorageUrl(""),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(failedCompleted));

    JsonResponse response2 = failedCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat(response2, isUnprocessableEntity());
    JsonObject error = extractErrorObject(response2);
    assertThat("unexpected error message", error,
        anyOf(hasMessage("must not be null"), hasMessage("darf nicht null sein")));  // any server language
  }

  @Test
  public void cannotCreateRequestPolicyWithExistingName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException{
      CompletableFuture<JsonResponse> failedCompleted = new CompletableFuture<>();

      String reqPolicyName = "request_policy_name";

      List<RequestType> requestTypes = Collections.singletonList(RequestType.HOLD);

      //create a requestPolicy with reqPolicyName as name
      createDefaultRequestPolicy(UUID.randomUUID(), reqPolicyName,"test policy", requestTypes );

      //create another requestPolicy with the same name
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

      assertThat(format("Failed to create request policy: %s", response2.getBody()),
        response2.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));

      assertThat("unexpected error message" , response2.getBody().contains("duplicate key value"));
  }

  @Test
  public void cannotCreateRequestPolicyWithBadUID()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException{

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    List<RequestType> requestTypes = Collections.singletonList(RequestType.HOLD);
    String badId = "bad_uuid";

    RequestPolicy requestPolicy = new RequestPolicy();
    requestPolicy.withDescription("test policy");
    requestPolicy.withName("successful_get");
    requestPolicy.withRequestTypes(requestTypes);
    requestPolicy.withId(badId);

    client.post(requestPolicyStorageUrl(""),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat(format("Failed to create request policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
    assertThat(response.getBody(), containsString("Invalid UUID string"));
  }

  @Test
  public void canGetRequestPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CleanupRequestPolicyRecords();
    CompletableFuture<JsonResponse> createCompletedGet = new CompletableFuture<>();

    RequestPolicy requestPolicy1 = createDefaultRequestPolicy();
    RequestPolicy requestPolicy2 = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl(""), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompletedGet));

    JsonResponse responseGet = createCompletedGet.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject getResultsJson = responseGet.getJson();

    JsonArray requestPolicies = getResultsJson.getJsonArray("requestPolicies");
    assertThat(getResultsJson.getInteger("totalRecords"), is(2));

    validateRequestPolicy(requestPolicy1, requestPolicies.getJsonObject(0));
    validateRequestPolicy(requestPolicy2, requestPolicies.getJsonObject(1));
  }

  @Test
  public void canGetRequestPoliciesByIdUsingQuery()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    RequestPolicy requestPolicy1 = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("?query=id="+ requestPolicy1.getId()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject responseJson = responseGet.getJson();

    JsonArray requestPolicies = responseJson.getJsonArray("requestPolicies");
    assertThat(responseJson.getInteger("totalRecords"), is(1));

    JsonObject aPolicy = requestPolicies.getJsonObject(0);

    validateRequestPolicy(requestPolicy1, aPolicy);
  }

  @Test
  public void cannotGetRequestPoliciesByUsingNegativeLimit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    RequestPolicy requestPolicy = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("", "query", "(name=" + requestPolicy.getId() + ")", "limit", "-230"),
        StorageTestSuite.TENANT_ID,
        ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat(responseGet, isBadRequest());
    assertThat("expected error message not found", responseGet.getBody().toLowerCase(), containsString("limit"));
  }

  @Test
  public void cannotGetRequestPoliciesByUsingNegativeOffset()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    RequestPolicy requestPolicy = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("", "query", "(name=" + requestPolicy.getId() + ")", "offset", "-230"),
        StorageTestSuite.TENANT_ID,
        ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat(responseGet, isBadRequest());
    assertThat("expected error message not found", responseGet.getBody().toLowerCase(), containsString("offset"));
  }

  @Test
  public void cannotGetRequestPolicyByNonExistentName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    RequestPolicy requestPolicy = createDefaultRequestPolicy();

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("?query=name=" + requestPolicy.getName() + "blabblabla"), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to not get request-policy", responseGet.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject getResultsJson = responseGet.getJson();
    assertThat(getResultsJson.getInteger("totalRecords"), is(0));
  }

  @Test
  public void canGetRequestPolicyByName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    //create a couple of RequestPolicies
    createDefaultRequestPolicy();
    RequestPolicy requestPolicy2 = createDefaultRequestPolicy();

    //Get the latter created request policy by name
    client.get(requestPolicyStorageUrl("?query=name=" + requestPolicy2.getName()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject getResultsJson = responseGet.getJson();

    JsonArray requestPolicies = getResultsJson.getJsonArray("requestPolicies");
    assertThat(getResultsJson.getInteger("totalRecords"), is(1));

    JsonObject aPolicy = requestPolicies.getJsonObject(0);

    validateRequestPolicy(requestPolicy2, aPolicy);
  }

  @Test
  public void canGetRequestPolicyById()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.RECALL);

    createDefaultRequestPolicy();
    RequestPolicy req2 = createDefaultRequestPolicy(UUID.randomUUID(), "request2 name",
                              "request policy 2 descr", requestTypes );

    //Get the newly created request policies
    client.get(requestPolicyStorageUrl("/" + req2.getId()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse responseGet = getCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    JsonObject aPolicy = responseGet.getJson();

    validateRequestPolicy(req2, aPolicy);
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
    assertThat(responseGet, isNotFound());
    assertThat(responseGet.getBody().toLowerCase(), containsString("not found"));
  }

  @Test
  public void canDeleteRequestPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CleanupRequestPolicyRecords();

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

    assertThat(getResultsJson, is(notNullValue()));
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
    assertThat(responseGetVerify, isNotFound());
    assertThat(responseGetVerify.getBody().toLowerCase(), containsString("not found"));
  }

  @Test
  public void cannotDeleteRequestPoliciesByNonExistingId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> delCompleted = new CompletableFuture<>();

    //Delete existing policy
    client.delete(requestPolicyStorageUrl( "/" + UUID.randomUUID().toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(delCompleted));
    JsonResponse responseDel = delCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat("Failed to not delete all request policies", responseDel, isNotFound());
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

    validateRequestPolicy(requestPolicy, aPolicy);
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
    assertThat(response, isBadRequest());
    assertThat(response.getBody(), containsString("already exists"));
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
    assertThat("Failed to update request-policy", response, isBadRequest());
    assertThat("Error message does not contain keyword 'already existed'", response.getBody(), containsString("already exists"));
  }

  @Test
  public void cannotUpdateRequestPolicyWithoutName()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> failedCompleted = new CompletableFuture<>();
    List<RequestType> requestTypes = Collections.singletonList(RequestType.HOLD);

    RequestPolicy requestPolicy = createDefaultRequestPolicy();

    //update requestPolicy
    requestPolicy.setDescription("new description!!!");
    requestPolicy.setName(null);
    requestPolicy.setRequestTypes(requestTypes);

    client.put(requestPolicyStorageUrl("/" + requestPolicy.getId()),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(failedCompleted));

    JsonResponse response2 = failedCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

    assertThat(response2, isUnprocessableEntity());

    JsonObject error = extractErrorObject(response2);
    assertThat("unexpected error message", error,
        anyOf(hasMessage("must not be null"), hasMessage("darf nicht null sein")));  // any server language
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
  public void createdRequestHasCreationMetadata()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final String METADATA_PROPERTY = "metadata";
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);

    RequestPolicy requestPolicy = new RequestPolicy();
    requestPolicy.withDescription("Sample request policy");
    requestPolicy.withName("Request Policy 1");
    requestPolicy.withRequestTypes(requestTypes);
    requestPolicy.withId(UUID.randomUUID().toString());

    String creatorId = UUID.randomUUID().toString();

    DateTime requestMade = DateTime.now();

    //Create requestPolicy
    client.post(requestPolicyStorageUrl(""),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      creatorId,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to create new request-policy", response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject createdResponse = response.getJson();

    assertThat("Request should have metadata property",
      createdResponse.containsKey(METADATA_PROPERTY), is(true));

    JsonObject metadata = createdResponse.getJsonObject(METADATA_PROPERTY);

    assertThat("Request should have created user",
      metadata.getString("createdByUserId"), is(creatorId));

    //RAML-Module-Builder also populates updated information at creation time
    assertThat("Request should have updated user",
      metadata.getString("updatedByUserId"), is(creatorId));

    assertThat("Request should have update date close to when request was made",
      metadata.getString("updatedDate"),
      is(withinSecondsAfter(Seconds.seconds(2), requestMade)));
  }

  @Test
  public void canCreateRequestPolicyWithNoAllowedServicePoints()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    RequestPolicy policy = new RequestPolicy()
      .withName("Request policy with no allowed service points")
      .withAllowedServicePoints(new AllowedServicePoints());

    JsonResponse response = createRequestPolicy(policy);
    assertThat(response, isCreated());
    assertThat(response.getJson().getJsonObject("allowedServicePoints"), emptyIterable());
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canCreateRequestPolicyWithNullListOfAllowedServicePoints(RequestType requestType)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    RequestPolicy policy = new RequestPolicy()
      .withName("Request policy with no allowed service points")
      .withAllowedServicePoints(new AllowedServicePoints());
    JsonObject requestPolicyJson = JsonObject.mapFrom(policy);
    requestPolicyJson.getJsonObject("allowedServicePoints").putNull(requestType.value());
    JsonResponse response = createRequestPolicy(requestPolicyJson);

    assertThat(response, isCreated());
    assertThat(response.getJson().getJsonObject("allowedServicePoints"), emptyIterable());
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canCreateRequestPolicyWithAllowedServicePoints(RequestType requestType)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    String servicePointId = randomId();
    createStubForServicePoints(new Servicepoint()
      .withId(servicePointId)
      .withPickupLocation(true));

    JsonResponse response = createRequestPolicy(buildRequestPolicy(requestType, List.of(servicePointId)));
    assertThat(response, isCreated());
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canNotCreateRequestPolicyWithEmptyListOfAllowedServicePoints(RequestType requestType)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    JsonResponse response = createRequestPolicy(buildRequestPolicy(requestType, emptyList()));
    assertThat(response, isUnprocessableEntity());
    assertThat(extractErrorObject(response).getString("message"),
      containsString("size must be between 1 and 2147483647"));
  }

  @Test
  @Parameters(source = RequestType.class)
  public void duplicateAllowedServicePointIdsAreRemoved(RequestType requestType)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    String servicePointId = randomId();
    createStubForServicePoints(new Servicepoint()
      .withId(servicePointId)
      .withPickupLocation(true));

    JsonObject requestPolicy = buildRequestPolicy(requestType, List.of(servicePointId, servicePointId));
    JsonResponse response = createRequestPolicy(requestPolicy);

    assertThat(response, isCreated());
    JsonArray allowedServicePoints = response.getJson()
      .getJsonObject("allowedServicePoints")
      .getJsonArray(requestType.value());

    assertThat(allowedServicePoints, iterableWithSize(1));
    assertThat(allowedServicePoints.getString(0), is(servicePointId));
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canNotCreateRequestPolicyWithNonExistentAllowedServicePoint(RequestType requestType)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    createStubForServicePoints(emptyList());
    final String servicePointId = randomId();
    JsonObject requestPolicy = buildRequestPolicy(requestType, List.of(servicePointId));
    JsonResponse response = createRequestPolicy(requestPolicy);

    assertThat(response, isValidationResponseWhich(allOf(
      hasMessage("One or more Pickup locations are no longer available"),
      hasCode(INVALID_ALLOWED_SERVICE_POINT_ERROR_CODE)
    )));
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canNotUpdateRequestPolicyWithNonExistentAllowedServicePoint(RequestType requestType)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    createStubForServicePoints(emptyList());
    final String servicePointId = randomId();
    JsonObject requestPolicy = buildRequestPolicy(requestType, List.of(servicePointId));
    JsonResponse response = updateRequestPolicy(requestPolicy);

    assertThat(response, isValidationResponseWhich(allOf(
      hasMessage("One or more Pickup locations are no longer available"),
      hasCode(INVALID_ALLOWED_SERVICE_POINT_ERROR_CODE)
    )));
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canNotCreateRequestPolicyWithNonPickupLocationServicePoint(RequestType requestType)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    final String servicePointId = randomId();
    Servicepoint servicePoint = new Servicepoint()
      .withId(servicePointId)
      .withPickupLocation(false);

    createStubForServicePoints(servicePoint);
    JsonObject requestPolicy = buildRequestPolicy(requestType, List.of(servicePointId));
    JsonResponse response = createRequestPolicy(requestPolicy);

    assertThat(response, isValidationResponseWhich(allOf(
      hasMessage("One or more Pickup locations are no longer available"),
      hasCode(INVALID_ALLOWED_SERVICE_POINT_ERROR_CODE)
    )));
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canNotUpdateRequestPolicyWithNonPickupLocationServicePoint(RequestType requestType)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    final String servicePointId = randomId();
    Servicepoint servicePoint = new Servicepoint()
      .withId(servicePointId)
      .withPickupLocation(false);

    createStubForServicePoints(servicePoint);
    JsonObject requestPolicy = buildRequestPolicy(requestType, List.of(servicePointId));
    JsonResponse response = updateRequestPolicy(requestPolicy);

    assertThat(response, isValidationResponseWhich(allOf(
      hasMessage("One or more Pickup locations are no longer available"),
      hasCode(INVALID_ALLOWED_SERVICE_POINT_ERROR_CODE)
    )));
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canNotCreateRequestPolicyWhenAllowedServicePointHasNullPickupLocation(
    RequestType requestType) throws MalformedURLException, ExecutionException, InterruptedException,
    TimeoutException {

    final String servicePointId = randomId();
    Servicepoint servicePoint = new Servicepoint()
      .withId(servicePointId)
      .withPickupLocation(null);

    createStubForServicePoints(servicePoint);
    JsonObject requestPolicy = buildRequestPolicy(requestType, List.of(servicePointId));
    JsonResponse response = createRequestPolicy(requestPolicy);

    assertThat(response, isValidationResponseWhich(allOf(
      hasMessage("One or more Pickup locations are no longer available"),
      hasCode(INVALID_ALLOWED_SERVICE_POINT_ERROR_CODE)
    )));
  }

  @Test
  @Parameters(source = RequestType.class)
  public void canNotUpdateRequestPolicyWhenAllowedServicePointHasNullPickupLocation(
    RequestType requestType) throws MalformedURLException, ExecutionException, InterruptedException,
    TimeoutException {

    final String servicePointId = randomId();
    Servicepoint servicePoint = new Servicepoint()
      .withId(servicePointId)
      .withPickupLocation(null);

    createStubForServicePoints(servicePoint);
    JsonObject requestPolicy = buildRequestPolicy(requestType, List.of(servicePointId));
    JsonResponse response = updateRequestPolicy(requestPolicy);

    assertThat(response, isValidationResponseWhich(allOf(
      hasMessage("One or more Pickup locations are no longer available"),
      hasCode(INVALID_ALLOWED_SERVICE_POINT_ERROR_CODE)
    )));
  }

  @Test
  public void singleErrorIsReturnedWhenUsingMultipleInvalidServicePointIds()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    String nonExistentServicePointId = randomId();
    String nonPickupLocationServicePointId = randomId();
    String nullPickupLocationServicePointId = randomId();
    Set<String> servicePointIds = Set.of(nonExistentServicePointId, nonPickupLocationServicePointId,
      nullPickupLocationServicePointId);

    Servicepoint nonPickupLocationServicePoint = new Servicepoint()
      .withId(nonPickupLocationServicePointId)
      .withPickupLocation(false);

    Servicepoint nullPickupLocationServicePoint = new Servicepoint()
      .withId(nullPickupLocationServicePointId)
      .withPickupLocation(null);

    createStubForServicePoints(List.of(nonPickupLocationServicePoint, nullPickupLocationServicePoint));

    JsonResponse response = createRequestPolicy(new RequestPolicy()
      .withName("Test request policy")
      .withRequestTypes(List.of(RequestType.PAGE, RequestType.HOLD, RequestType.RECALL))
      .withAllowedServicePoints(new AllowedServicePoints()
        .withPage(servicePointIds)
        .withHold(servicePointIds)
        .withRecall(servicePointIds)));

    assertThat(response, isUnprocessableEntity());
    assertThat(response.getJson().getJsonArray("errors"), iterableWithSize(1));

    assertThat(response, isValidationResponseWhich(allOf(
      hasMessage("One or more Pickup locations are no longer available"),
      hasCode(INVALID_ALLOWED_SERVICE_POINT_ERROR_CODE)
    )));
  }

  @Test
  @Parameters(source = RequestType.class)
  public void requestPolicyIsCreatedAndUpdatedWhenAllowedServicePointContainsStaffSlipsConfiguration(
    RequestType requestType) throws MalformedURLException, ExecutionException, InterruptedException,
    TimeoutException {

    String servicePointId = randomId();
    JsonObject servicePoint = new JsonObject()
      .put("id", servicePointId)
      .put("pickupLocation", true)
      .put("staffSlips", new JsonArray()
        .add(new JsonObject()
          .put("id", randomId())
          .put("printByDefault", true)
        ));

    createStubForServicePoints(new JsonObject().put("servicepoints", new JsonArray().add(servicePoint)));
    JsonObject requestPolicy = buildRequestPolicy(requestType, List.of(servicePointId));
    JsonResponse postResponse = createRequestPolicy(requestPolicy);
    assertThat(postResponse, isCreated());
    JsonResponse putResponse = updateRequestPolicy(requestPolicy);
    assertThat(putResponse, isNoContent());
  }

  private static JsonObject buildRequestPolicy(RequestType requestType,
    List<String> allowedServicePointIds) {

    return new JsonObject()
      .put("id", randomId())
      .put("name", "Request policy for " + requestType.value())
      .put("requestTypes", new JsonArray().add(requestType.value()))
      .put("allowedServicePoints", new JsonObject()
        .put(requestType.value(), new JsonArray(allowedServicePointIds)));
  }

  private JsonResponse createRequestPolicy(RequestPolicy requestPolicy)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    return createRequestPolicy(JsonObject.mapFrom(requestPolicy));
  }

  private JsonResponse createRequestPolicy(JsonObject requestPolicy)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> postFuture = new CompletableFuture<>();
    client.post(requestPolicyStorageUrl(""), requestPolicy, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(postFuture));

    return postFuture.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse updateRequestPolicy(RequestPolicy requestPolicy)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> putFuture = new CompletableFuture<>();
    client.put(requestPolicyStorageUrl("/" + requestPolicy.getId()), requestPolicy,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(putFuture));

    return updateRequestPolicy(mapFrom(requestPolicy));
  }

  private JsonResponse updateRequestPolicy(JsonObject requestPolicy)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> putFuture = new CompletableFuture<>();
    client.put(requestPolicyStorageUrl("/" + requestPolicy.getString("id")), requestPolicy,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(putFuture));

    return putFuture.get(5, TimeUnit.SECONDS);
  }

  private URL requestPolicyStorageUrl(String path, String... parameterKeyValue) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/request-policy-storage/request-policies" + path, parameterKeyValue);
  }

  private RequestPolicy createDefaultRequestPolicy()  throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    List<RequestType> requestTypes = Arrays.asList( RequestType.HOLD, RequestType.PAGE);
    UUID id1 = UUID.randomUUID();

    REQ_POLICY_NAME_INCR++;

    return createDefaultRequestPolicy(id1, DEFAULT_REQUEST_POLICY_NAME + REQ_POLICY_NAME_INCR, "test policy", requestTypes);
  }

  private RequestPolicy createDefaultRequestPolicy(UUID id, String name, String descr, List<RequestType> requestTypes)  throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    RequestPolicy requestPolicy = new RequestPolicy();
    requestPolicy.withDescription(descr);
    requestPolicy.withName(name);
    requestPolicy.withRequestTypes(requestTypes);
    if (id != null){
      requestPolicy.withId(id.toString());    }

    //Create requestPolicy
    client.post(requestPolicyStorageUrl(""),
      requestPolicy,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    assertThat("Failed to create new request-policy", response, isCreated());

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(notNullValue()));
    assertThat(representation.getString("name"), is(name));
    assertThat(representation.getString("description"), is(descr));
    if (id != null){
      assertThat(representation.getString("id"), is(id.toString()));
    }

    return requestPolicy;
  }

  private JsonObject extractErrorObject(JsonResponse response){
    JsonObject responseJson = response.getJson();
    JsonArray errors = responseJson.getJsonArray("errors");

    return errors.getJsonObject(0);
  }

  private void validateRequestPolicy(RequestPolicy expectedPolicy, JsonObject outcomePolicy){

    assertThat(outcomePolicy.getString("id"), is( expectedPolicy.getId()) );
    assertThat(outcomePolicy.getString("name"), is( expectedPolicy.getName() ));
    assertThat(outcomePolicy.getString("description"), is( expectedPolicy.getDescription() ));

    JsonArray resultRequestTypes = outcomePolicy.getJsonArray("requestTypes");

    for ( Object type : resultRequestTypes) {
      assertThat("requestType returned: " + type.toString() + " does not exist in input list",
        expectedPolicy.getRequestTypes().contains(RequestType.fromValue(type.toString())));
    }
  }

  private void CleanupRequestPolicyRecords()
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

  private void createStubForServicePoints(Servicepoint servicePoint) {
    createStubForServicePoints(List.of(servicePoint));
  }

  private void createStubForServicePoints(List<Servicepoint> servicePoints) {
   createStubForServicePoints(mapFrom(new Servicepoints().withServicepoints(servicePoints)));
  }

  private void createStubForServicePoints(JsonObject servicePoints) {
    StorageTestSuite.getWireMockServer()
      .stubFor(WireMock.get(urlPathMatching(SERVICE_POINTS_URL + ".*"))
        .willReturn(ok().withBody(servicePoints.encodePrettily())));
  }
}
