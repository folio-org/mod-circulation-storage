package org.folio.rest.api.loans;

import static org.folio.rest.support.http.InterfaceUrls.loanStorageUrl;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.UUIDMatchers.isUUID;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.rest.support.matchers.ValidationResponseMatchers.isValidationResponseWhich;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.MultipleRecords;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.joda.time.DateTime;
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
  public void shouldSucceedEvenWhenNoLoans()
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

    assertThat("Anonymized loans should still be present",
      loansClient.getAll().getTotalRecords(), is(1));
  }

  @Test
  public void shouldNotAnonymizeOpenLoans()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final UUID userId = UUID.randomUUID();

    final LoanRequestBuilder loanForUser = new LoanRequestBuilder()
      .withUserId(userId);

    final String firstOpenLoanId = loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    final String secondOpenLoanId = loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    anonymizeLoansFor(userId);

    hasOpenLoansForUser(userId, firstOpenLoanId, secondOpenLoanId);

    assertThat("Anonymized loans should still be present",
      loansClient.getAll().getTotalRecords(), is(2));
  }

  @Test
  public void shouldOnlyAnonymizeClosedLoansWhenBothArePresent()
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
      .withId(UUID.randomUUID())).getId();

    loansClient.create(loanForUser
      .closed()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    final String firstOpenLoanId = loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    final String secondOpenLoanId = loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    loansClient.create(loanForUser
      .closed()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    anonymizeLoansFor(userId);

    hasOpenLoansForUser(userId, firstOpenLoanId, secondOpenLoanId);

    assertThat("Anonymized loans should still be present",
      loansClient.getAll().getTotalRecords(), is(5));
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

    assertThat("Anonymized loans should still be present",
      loansClient.getAll().getTotalRecords(), is(1));
  }

  @Test
  public void shouldAnonymizeSingleClosedLoanHistoryForUser()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final UUID userId = UUID.randomUUID();

    final IndividualResource loan = loansClient.create(
      new LoanRequestBuilder()
        .open()
        .withUserId(userId));

    loansClient.replace(loan.getId(), LoanRequestBuilder.from(loan.getJson())
      .withReturnDate(DateTime.now())
      .closed());

    anonymizeLoansFor(userId);

    final MultipleRecords<JsonObject> historyRecords
      = getLoanActionHistoryForUser(userId);

    assertThat("Should be no history records for user",
      historyRecords.getRecords().size(), is(0));

    assertThat("Should be no history records for user",
      historyRecords.getTotalRecords(), is(0));
  }

  @Test
  public void shouldNotAnonymizeLoanActionHistoryForOpenLoans()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final UUID userId = UUID.randomUUID();

    final LoanRequestBuilder loanForUser = new LoanRequestBuilder()
      .withUserId(userId);

    final String firstOpenLoanId = loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    final String secondOpenLoanId = loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    anonymizeLoansFor(userId);

    hasLoanHistoryForUser(userId, firstOpenLoanId, secondOpenLoanId);
  }

  @Test
  public void shouldNotAnonymizeLoansHistoryForOtherUser()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final UUID firstUserId = UUID.randomUUID();
    final UUID secondUserId = UUID.randomUUID();

    final IndividualResource loanForOtherUser = loansClient.create(
      new LoanRequestBuilder()
        .closed()
        .withUserId(firstUserId));

    anonymizeLoansFor(secondUserId);

    hasLoanHistoryForUser(firstUserId, loanForOtherUser.getId());
  }

  @Test
  public void shouldOnlyAnonymizeClosedLoansHistoryWhenBothArePresent()
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
      .withId(UUID.randomUUID())).getId();

    loansClient.create(loanForUser
      .closed()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    final String firstOpenLoanId = loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    final String secondOpenLoanId = loansClient.create(loanForUser
      .open()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    loansClient.create(loanForUser
      .closed()
      .withItemId(UUID.randomUUID())
      .withId(UUID.randomUUID())).getId();

    anonymizeLoansFor(userId);

    hasLoanHistoryForUser(userId, firstOpenLoanId, secondOpenLoanId);
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

  private MultipleRecords<JsonObject> getLoansForUser(UUID userId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return loansClient.getMany(String.format("userId==%s", userId));
  }

  private MultipleRecords<JsonObject> getLoanActionHistoryForUser(UUID userId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<JsonResponse> fetchHistoryCompleted = new CompletableFuture<>();

    client.get(StorageTestSuite.storageUrl("/loan-storage/loan-history",
        "query", String.format("loan.userId==%s", userId)),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(fetchHistoryCompleted));

    final JsonResponse historyResponse = fetchHistoryCompleted.get(5, TimeUnit.SECONDS);

    return MultipleRecords.fromJson(historyResponse.getJson(), "loans-history");
  }

  private void hasOpenLoansForUser(UUID userId, String... openLoanIds)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final MultipleRecords<JsonObject> wrappedLoans = getLoansForUser(userId);

    assertThat(wrappedLoans.getTotalRecords(), is(openLoanIds.length));

    final Collection<JsonObject> fetchedLoans = wrappedLoans.getRecords();

    assertThat(fetchedLoans.size(), is(openLoanIds.length));

    final List<String> fetchedLoanIds = fetchedLoans.stream()
      .map(loan -> loan.getString("id"))
      .collect(Collectors.toList());

    assertThat(fetchedLoanIds, containsInAnyOrder(openLoanIds));
  }

  private void hasLoanHistoryForUser(UUID userId, String... openLoanIds)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final MultipleRecords<JsonObject> wrappedLoanHistoryActions
      = getLoanActionHistoryForUser(userId);

    final Collection<JsonObject> fetchedLoanActionHistoryEntries
      = wrappedLoanHistoryActions.getRecords();

    //Needs to be distinct as could be multiple entries per loan
    final List<String> fetchedLoanHistoryIds = fetchedLoanActionHistoryEntries
      .stream()
      .map(entry -> entry.getJsonObject("loan").getString("id"))
      .distinct()
      .collect(Collectors.toList());

    assertThat(fetchedLoanActionHistoryEntries.size(), is(openLoanIds.length));

    assertThat(fetchedLoanHistoryIds, containsInAnyOrder(openLoanIds));
  }

  @Test
  public void shouldRejectInvalidUUID()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    final CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl("/anonymize/" + "foo"),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(postCompleted));

    final JsonResponse postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse, isValidationResponseWhich(allOf(
      hasMessage("Invalid user ID, should be a UUID"),
      hasParameter("userId", "foo"))));
  }
}
