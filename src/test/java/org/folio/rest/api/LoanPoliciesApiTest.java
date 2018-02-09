package org.folio.rest.api;


import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.LoanPolicyRequestBuilder;
import org.hamcrest.junit.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.periodJsonObjectMatcher.matchesPeriod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class LoanPoliciesApiTest {
  private static HttpClient client = new HttpClient(StorageTestSuite.getVertx());

  @Before
  public void beforeEach()
    throws MalformedURLException {

    System.out.println("attempting full delete LoanPoliciesApiTest.....");
    StorageTestSuite.deleteAll(loanPolicyStorageUrl());
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("loan_policy");
  }

  @Test
  public void loanPolicyWithFixedDate() throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID fddId = UUID.randomUUID();

    ///////////// create fixed due date to use as foreign key
    JsonObject fdd = FixedDueDateApiTest.createFixedDueDate(fddId.toString(), "semester_test", "desc");
    JsonObject fddSchedule = FixedDueDateApiTest.createSchedule("2017-01-01T10:00:00.000+0000",
      "2017-03-03T10:00:00.000+0000", "2017-04-04T10:00:00.000+0000");
    fdd.put(FixedDueDateApiTest.SCHEDULE_SECTION, new JsonArray().add(fddSchedule));

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.post(FixedDueDateApiTest.dueDateURL(), fdd, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));
    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    JsonObject representation = response.getJson();
    assertThat(representation.getString("id"), is(fddId.toString()));
    ////////////////////////////////////////////////////////////

    UUID id1 = UUID.randomUUID();

    //////////// create loan policy with foreign key to fdd
    JsonObject loanPolicyRequest = new LoanPolicyRequestBuilder()
      .withId(id1)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();
    loanPolicyRequest.getJsonObject("loansPolicy").put("fixedDueDateScheduleId", fddId.toString());
    CompletableFuture<JsonResponse> createLPCompleted = new CompletableFuture<>();
    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createLPCompleted));
    JsonResponse lpResponse = createLPCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create loan policy: %s", lpResponse.getBody()),
      lpResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    ////////////////////////////////////////////////////////

    ////////////validation error - renewable = true + different period = true + fixed -> needs fk
    CompletableFuture<JsonResponse> createpdateV1Completed = new CompletableFuture<>();
    JsonObject loanPolicyRequest3 = new LoanPolicyRequestBuilder()
        .withId(id1)
        .withName("Example Loan Policy")
        .withDescription("An example loan policy")
        .create();
    loanPolicyRequest3.getJsonObject("loansPolicy").put("profileId", "Fixed");
    client.put(loanPolicyStorageUrl("/"+id1), loanPolicyRequest3, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createpdateV1Completed));
    JsonResponse updateV1response = createpdateV1Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", updateV1response.getBody()),
      updateV1response.getStatusCode(), is(422));
    //////////////////////////////////////////////////////////////

    ///////////non-existent foreign key
    UUID id2 = UUID.randomUUID();
    JsonObject badLoanPolicyRequest = new LoanPolicyRequestBuilder()
        .withId(id2)
        .withName("Example Loan Policy")
        .withDescription("An example loan policy")
        .create();
    badLoanPolicyRequest.getJsonObject("loansPolicy")
      .put("fixedDueDateScheduleId", "cb201cd6-296c-4457-9ac4-617d9584e27b");
    CompletableFuture<JsonResponse> createBadLPCompleted = new CompletableFuture<>();
    client.post(loanPolicyStorageUrl(),
      badLoanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createBadLPCompleted));
    JsonResponse badlpResponse = createBadLPCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create loan policy: %s", badlpResponse.getBody()),
      badlpResponse.getStatusCode(), is(500));
    //////////////////////////////////////////

    ///////////bad foreign key
    id2 = UUID.randomUUID();
    JsonObject bad2LoanPolicyRequest = new LoanPolicyRequestBuilder()
        .withId(id2)
        .withName("Example Loan Policy")
        .withDescription("An example loan policy")
        .create();
    bad2LoanPolicyRequest.getJsonObject("loansPolicy")
      .put("fixedDueDateScheduleId", "1234567890");
    CompletableFuture<JsonResponse> createBadLP2Completed = new CompletableFuture<>();
    client.post(loanPolicyStorageUrl(),
      bad2LoanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createBadLP2Completed));
    JsonResponse badlpResponse2 = createBadLP2Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create loan policy: %s", badlpResponse2.getBody()),
      badlpResponse2.getStatusCode(), is(500));
    //////////////////////////////////////////

    id2 = UUID.randomUUID();

    //////////// create loan policy with fk to jsonb->'renewalsPolicy'->>'alternateFixedDueDateScheduleId'
    JsonObject loanPolicyRequest4 = new LoanPolicyRequestBuilder()
      .withId(id2)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();
    JsonObject renewalsPolicy = loanPolicyRequest4.getJsonObject("renewalsPolicy");
    renewalsPolicy.put("alternateFixedDueDateScheduleId", fddId.toString());
    CompletableFuture<JsonResponse> createLPHeirarchyCompleted = new CompletableFuture<>();
    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest4, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createLPHeirarchyCompleted));
    JsonResponse lpHierarchyResponse = createLPHeirarchyCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create loan policy: %s", lpHierarchyResponse.getBody()),
      lpHierarchyResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    ///validation, fk to fixedDueDateScheduleId required once profileid = fixed
    CompletableFuture<JsonResponse> createpdateV2Completed = new CompletableFuture<>();
    JsonObject loanPolicyRequest8 = new LoanPolicyRequestBuilder()
        .withId(id2)
        .withName("Example Loan Policy")
        .withDescription("An example loan policy")
        .create();
    loanPolicyRequest8.getJsonObject("loansPolicy").put("profileId", "Fixed");
    loanPolicyRequest8.put("renewable", false);
    client.put(loanPolicyStorageUrl("/"+id2), loanPolicyRequest8, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createpdateV2Completed));
    JsonResponse updateV2response = createpdateV2Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", updateV2response.getBody()),
      updateV2response.getStatusCode(), is(422));

    //update alternateFixedDueDateScheduleId with a bad (non existent) id
    CompletableFuture<Response> updateCompleted = new CompletableFuture<>();
    renewalsPolicy.put("alternateFixedDueDateScheduleId", "ab201cd6-296c-4457-9ac4-617d9584e27b");
    client.put(loanPolicyStorageUrl("/"+id2),
      loanPolicyRequest4, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(updateCompleted));
    Response updateResponse = updateCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", loanPolicyRequest4.encodePrettily()),
      updateResponse.getStatusCode(), is(500));
    ////////////////////////////////////////////////////////

    //delete loan policy //////////////////
    System.out.println("Running: DELETE " + loanPolicyStorageUrl("/"+id2));
    CompletableFuture<Response> delCompleted2 = new CompletableFuture<>();
    client.delete(loanPolicyStorageUrl("/"+id2), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delCompleted2));
    Response delCompleted4Response = delCompleted2.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", loanPolicyStorageUrl("/"+id2)),
      delCompleted4Response.getStatusCode(), is(204));
    ///////////////////////////////////////

    //delete loan policy //////////////////
    System.out.println("Running: DELETE " + loanPolicyStorageUrl("/"+id1));
    CompletableFuture<Response> delCompleted9 = new CompletableFuture<>();
    client.delete(loanPolicyStorageUrl("/"+id1), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delCompleted9));
    Response delCompleted5Response = delCompleted9.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", loanPolicyStorageUrl("/"+id1)),
      delCompleted5Response.getStatusCode(), is(204));
    ///////////////////////////////////////

    //// try to delete the fdd - not allowed since referenced by loan policy ///////////////////////
    System.out.println("Running: DELETE " + FixedDueDateApiTest.dueDateURL("/"+fddId.toString()));
    CompletableFuture<Response> delCompleted3 = new CompletableFuture<>();
    client.delete(FixedDueDateApiTest.dueDateURL("/"+fddId.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delCompleted3));
    Response delCompleted6Response = delCompleted3.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", FixedDueDateApiTest.dueDateURL("/"+fddId.toString())),
      delCompleted6Response.getStatusCode(), is(204));
    //////////////////////////////////////////////////////////////////////////////////////////

    //// try to delete all fdds (uses cascade so will succeed) ///////////////////////
    System.out.println("Running: DELETE " + FixedDueDateApiTest.dueDateURL());
    CompletableFuture<Response> delAllCompleted = new CompletableFuture<>();
    client.delete(FixedDueDateApiTest.dueDateURL(), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delAllCompleted));
    Response delAllCompleted4Response = delAllCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", FixedDueDateApiTest.dueDateURL()),
      delAllCompleted4Response.getStatusCode(), is(204));
    //////////////////////////////////////////////////////////////////////////////////////////

    }

  @Test
  public void canCreateALoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = new LoanPolicyRequestBuilder()
      .withId(id)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("name"), is("Example Loan Policy"));
    assertThat(representation.getString("description"), is("An example loan policy"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(true));

    assertThat(representation.containsKey("loansPolicy"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("ROLLING"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(1, "MONTH"));
    assertThat(loansPolicy.getString("closedLibraryDueDateManagementId"), is("KEEP_CURRENT_DATE"));
    assertThat(loansPolicy.getJsonObject("existingRequestsPeriod"), matchesPeriod(1, "WEEK"));
    assertThat(loansPolicy.getJsonObject("gracePeriod"), matchesPeriod(7, "DAYS"));

    assertThat(representation.containsKey("renewalsPolicy"), is(true));

    JsonObject renewalsPolicy = representation.getJsonObject("renewalsPolicy");

    assertThat(renewalsPolicy.getBoolean("unlimited"), is(true));
    assertThat(renewalsPolicy.getString("renewFromId"), is("CURRENT_DUE_DATE"));
    assertThat(renewalsPolicy.getBoolean("differentPeriod"), is(true));
    assertThat(renewalsPolicy.getJsonObject("period"), matchesPeriod(30, "DAYS"));
  }

  @Test
  public void canCreateALoanPolicyWithoutAnId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    JsonObject loanPolicyRequest = new LoanPolicyRequestBuilder()
      .withNoId()
      .create();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(notNullValue()));
  }

  @Test
  public void canCreateALoanPolicyAtASpecificLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = new LoanPolicyRequestBuilder()
      .withId(id)
      .create();

    client.put(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse createResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", createResponse.getBody()),
      createResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get updated loan policy: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("description"), is("An example loan policy"));
    assertThat(representation.getString("name"), is("Example Loan Policy"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(true));

    assertThat(representation.containsKey("loansPolicy"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("ROLLING"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(1, "MONTH"));
    assertThat(loansPolicy.getString("closedLibraryDueDateManagementId"), is("KEEP_CURRENT_DATE"));
    assertThat(loansPolicy.getJsonObject("existingRequestsPeriod"), matchesPeriod(1, "WEEK"));
    assertThat(loansPolicy.getJsonObject("gracePeriod"), matchesPeriod(7, "DAYS"));

    assertThat(representation.containsKey("renewalsPolicy"), is(true));

    JsonObject renewalsPolicy = representation.getJsonObject("renewalsPolicy");

    assertThat(renewalsPolicy.getBoolean("unlimited"), is(true));
    assertThat(renewalsPolicy.getString("renewFromId"), is("CURRENT_DUE_DATE"));
    assertThat(renewalsPolicy.getBoolean("differentPeriod"), is(true));
    assertThat(renewalsPolicy.getJsonObject("period"), matchesPeriod(30, "DAYS"));
  }

  @Test
  public void canGetALoanPolicyById()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    createLoanPolicy(new LoanPolicyRequestBuilder().withId(id).create());

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get loan policy: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(notNullValue()));
    assertThat(representation.getString("description"), is("An example loan policy"));
    assertThat(representation.getString("name"), is("Example Loan Policy"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(true));

    assertThat(representation.containsKey("loansPolicy"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("ROLLING"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(1, "MONTH"));
    assertThat(loansPolicy.getString("closedLibraryDueDateManagementId"), is("KEEP_CURRENT_DATE"));
    assertThat(loansPolicy.getJsonObject("existingRequestsPeriod"), matchesPeriod(1, "WEEK"));
    assertThat(loansPolicy.getJsonObject("gracePeriod"), matchesPeriod(7, "DAYS"));

    assertThat(representation.containsKey("renewalsPolicy"), is(true));

    JsonObject renewalsPolicy = representation.getJsonObject("renewalsPolicy");

    assertThat(renewalsPolicy.getBoolean("unlimited"), is(true));
    assertThat(renewalsPolicy.getString("renewFromId"), is("CURRENT_DUE_DATE"));
    assertThat(renewalsPolicy.getBoolean("differentPeriod"), is(true));
    assertThat(renewalsPolicy.getJsonObject("period"), matchesPeriod(30, "DAYS"));
  }

  @Test
  public void cannotGetLoanPolicyForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonResponse getResponse = getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canUpdateAnExistingLoanPolicyByReplacingItsRepresentation()
  throws InterruptedException,
  MalformedURLException,
  TimeoutException,
  ExecutionException {

    UUID id = UUID.randomUUID();

    createLoanPolicy(new LoanPolicyRequestBuilder().withId(id).create());

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    JsonObject loanPolicyRequest = new LoanPolicyRequestBuilder()
      .withId(id)
      .withName("A Different Name")
      .withDescription("A different description")
      .create();

    client.put(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    JsonResponse updateResponse = updateCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan policy: %s", updateResponse.getBody()),
      updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get updated loan policy: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("name"), is("A Different Name"));
    assertThat(representation.getString("description"), is("A different description"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(true));

    assertThat(representation.containsKey("loansPolicy"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("ROLLING"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(1, "MONTH"));
    assertThat(loansPolicy.getString("closedLibraryDueDateManagementId"), is("KEEP_CURRENT_DATE"));
    assertThat(loansPolicy.getJsonObject("existingRequestsPeriod"), matchesPeriod(1, "WEEK"));
    assertThat(loansPolicy.getJsonObject("gracePeriod"), matchesPeriod(7, "DAYS"));

    assertThat(representation.containsKey("renewalsPolicy"), is(true));

    JsonObject renewalsPolicy = representation.getJsonObject("renewalsPolicy");

    assertThat(renewalsPolicy.getBoolean("unlimited"), is(true));
    assertThat(renewalsPolicy.getString("renewFromId"), is("CURRENT_DUE_DATE"));
    assertThat(renewalsPolicy.getBoolean("differentPeriod"), is(true));
    assertThat(renewalsPolicy.getJsonObject("period"), matchesPeriod(30, "DAYS"));
  }

  @Test
  public void canPageLoanPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    createLoanPolicy(new LoanPolicyRequestBuilder().create());
    createLoanPolicy(new LoanPolicyRequestBuilder().create());
    createLoanPolicy(new LoanPolicyRequestBuilder().create());
    createLoanPolicy(new LoanPolicyRequestBuilder().create());
    createLoanPolicy(new LoanPolicyRequestBuilder().create());
    createLoanPolicy(new LoanPolicyRequestBuilder().create());
    createLoanPolicy(new LoanPolicyRequestBuilder().create());

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture<>();

    client.get(loanPolicyStorageUrl() + "?limit=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(loanPolicyStorageUrl() + "?limit=4&offset=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    JsonResponse firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat(String.format("Failed to get first page of loan policies: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    MatcherAssert.assertThat(String.format("Failed to get second page of loans policies: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageLoans = firstPage.getJsonArray("loanPolicies");
    JsonArray secondPageLoans = secondPage.getJsonArray("loanPolicies");

    MatcherAssert.assertThat(firstPageLoans.size(), is(4));
    MatcherAssert.assertThat(firstPage.getInteger("totalRecords"), is(7));

    MatcherAssert.assertThat(secondPageLoans.size(), is(3));
    MatcherAssert.assertThat(secondPage.getInteger("totalRecords"), is(7));
  }

  @Test
  public void canDeleteALoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> deleteCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    createLoanPolicy(new LoanPolicyRequestBuilder().withId(id).create());

    client.delete(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(deleteCompleted));

    JsonResponse createResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to delete loan policy: %s", createResponse.getBody()),
      createResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Found a deleted loan policy: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  static URL loanPolicyStorageUrl() throws MalformedURLException {
    return loanPolicyStorageUrl("");
  }

  private static URL loanPolicyStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-policy-storage/loan-policies" + subPath);
  }

  private void createLoanPolicy(JsonObject loanPolicyRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
  }

  private JsonResponse getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    URL getInstanceUrl = loanPolicyStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }
}
