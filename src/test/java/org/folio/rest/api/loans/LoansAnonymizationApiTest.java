package org.folio.rest.api.loans;

import static org.folio.rest.support.JsonArrayHelper.toList;
import static org.folio.rest.support.http.InterfaceUrls.loanStorageUrl;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.UUIDMatchers.isUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class LoansAnonymizationApiTest extends ApiTests {
  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::loanStorageUrl);

  @Before
  public void beforeEach()
    throws MalformedURLException {

    StorageTestSuite.deleteAll(loanStorageUrl());
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("loan");
  }

  @Test
  public void shouldDoNothingWhenNoLoansForUser()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final UUID unknownUserId = UUID.randomUUID();

    anonymizeLoansFor(unknownUserId);
  }

  @Test
  public void shouldAnonymizeSingleClosedLoanForUser()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final UUID userId = UUID.randomUUID();

    final IndividualResource loan = loansClient.create(
      new LoanRequestBuilder()
        .closed()
        .withUserId(userId));

    anonymizeLoansFor(userId);

    final IndividualResource fetchedLoan = loansClient.getById(
      loan.getId());

    assertThat("Should no longer have a user ID",
      fetchedLoan.getJson().containsKey("userId"), is(false));
  }

  @Test
  public void shouldOnlyAnonymizeClosedLoans()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final UUID userId = UUID.randomUUID();

    final LoanRequestBuilder loanForUser = new LoanRequestBuilder()
      .withUserId(userId);

    loansClient.create(loanForUser
      .closed()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID()));

    loansClient.create(loanForUser
      .closed()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID()));

    loansClient.create(loanForUser
      .closed()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID()));

    loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID()));

    loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID()));

    anonymizeLoansFor(userId);

    final CompletableFuture<JsonResponse> fetchAllCompleted = new CompletableFuture<>();

    client.get(loanStorageUrl(),
      String.format("query=userId==%s", userId), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(fetchAllCompleted));

    final JsonResponse fetchedLoansResponse = fetchAllCompleted
      .get(5, TimeUnit.SECONDS);

    final JsonObject wrappedLoans = fetchedLoansResponse.getJson();

    assertThat(wrappedLoans.getInteger("totalRecords"), is(2));
    assertThat(toList(wrappedLoans, "loans").size(), is(2));
  }

  @Test
  public void shouldNotAnonymizeLoansForOtherUser()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final UUID firstUserId = UUID.randomUUID();
    final UUID secondUserId = UUID.randomUUID();

    final IndividualResource otherUsersLoan = loansClient.create(
      new LoanRequestBuilder()
        .closed()
        .withUserId(firstUserId));

    anonymizeLoansFor(secondUserId);

    final IndividualResource fetchedLoan = loansClient.getById(
      otherUsersLoan.getId());

    assertThat(fetchedLoan.getJson().getString("userId"), isUUID(firstUserId));
  }

  private void anonymizeLoansFor(UUID userId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<TextResponse> postCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl("/anonymize/" + userId),
      StorageTestSuite.TENANT_ID, ResponseHandler.text(postCompleted));

    final TextResponse postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse, isNoContent());
  }
}
