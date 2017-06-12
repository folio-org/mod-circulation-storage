package org.folio.rest.api;


import io.vertx.core.json.JsonObject;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.LoanPolicyRequestBuilder;
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

  @Test
  public void canCreateALoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = new LoanPolicyRequestBuilder()
      .withId(id)
      .create();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();

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
  public void canCreateALoanPolicyWithoutAnId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

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
  public void canGetALoanPolicyById()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = new LoanPolicyRequestBuilder()
      .withId(id)
      .create();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

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
    TimeoutException,
    UnsupportedEncodingException {

    JsonResponse getResponse = getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private static URL loanPolicyStorageUrl() throws MalformedURLException {
    return loanPolicyStorageUrl("");
  }

  private static URL loanPolicyStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-policy-storage/loan-policies" + subPath);
  }

  private JsonResponse getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    URL getInstanceUrl = loanPolicyStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

}
