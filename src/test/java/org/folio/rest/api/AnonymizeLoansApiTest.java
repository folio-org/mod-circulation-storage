package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.anonymizeLoansURL;
import static org.folio.rest.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AnonymizeLoansApiTest extends ApiTests {
  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::loanStorageUrl, "loans");

  private JsonArray loanIds;
  private int REQUEST_TIMEOUT = 500;

  @Before
  public void beforeEach()
    throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    StorageTestSuite.deleteAll(InterfaceUrls.loanStorageUrl());

    JsonObject loan1 = loansClient.create(
      new LoanRequestBuilder().withId(UUID.randomUUID())
        .withItemId(UUID.randomUUID())
        .withUserId(UUID.randomUUID())
        .closed()
        .create()).getJson();

    JsonObject loan2 = loansClient.create(
      new LoanRequestBuilder().withId(UUID.randomUUID())
        .withItemId(UUID.randomUUID())
        .withUserId(UUID.randomUUID())
        .closed()
        .create()).getJson();

    String loanId2 = loan2.getValue("id").toString();
    String loanId1 = loan1.getValue("id").toString();

    loanIds = new JsonArray().add(loanId1).add(loanId2);

  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("loan");
    StorageTestSuite.checkForMismatchedIDs("audit_loan");
  }

  @Test
  public void canAnonymizeLoans()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    JsonObject requestBody = new JsonObject().put("loanIds", loanIds);

    CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.post(anonymizeLoansURL(), requestBody, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(completed));
    JsonResponse response = completed.get(REQUEST_TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getJson().getJsonArray("anonymizedLoans"),
      equalTo(loanIds));
    assertThat(response.getStatusCode(), is(200));
    assertThat(loansClient.getById(loanIds.getString(0)).getJson(),
      isAnonymized());
    assertThat(loansClient.getById(loanIds.getString(1)).getJson(),
      isAnonymized());
  }

  @Test public void canNotAnonymizeEmptyList()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    JsonObject requestBody = new JsonObject().put("loanIds", new JsonArray());
    CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.post(anonymizeLoansURL(), requestBody, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(completed));
    JsonResponse response = completed.get(REQUEST_TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canAnonymizeInvalidAndValidUuids()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    JsonArray invalidUuids = new JsonArray().add("")
      .add("not valid")
      .add("null");

    JsonObject requestBody = new JsonObject().put("loanIds",
      new JsonArray().addAll(loanIds).addAll(invalidUuids));

    CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.post(anonymizeLoansURL(), requestBody, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(completed));
    JsonResponse response = completed.get(REQUEST_TIMEOUT, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(200));

    JsonObject responseJson = response.getJson();

    assertThat(responseJson.getJsonArray("anonymizedLoans"), equalTo(loanIds));

    JsonArray notAnonymizedLoans = responseJson.getJsonArray(
      "notAnonymizedLoans");
    assertThat(notAnonymizedLoans.size(), is(1));
    JsonObject notAnonimizedReasons = notAnonymizedLoans.getJsonObject(0);
    assertThat(notAnonimizedReasons.getString("reason"),
      equalTo("invalidLoanIds"));
    assertThat(notAnonimizedReasons.getJsonArray("loanIds"),
      equalTo(invalidUuids));
  }
}
