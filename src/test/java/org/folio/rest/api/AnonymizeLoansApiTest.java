package org.folio.rest.api;

import static java.lang.String.format;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.anonymizeLoansURL;
import static org.folio.rest.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.AnonymizeStorageLoansResponse;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.rest.support.matchers.LoanHistoryMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AnonymizeLoansApiTest extends ApiTests {
  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, TENANT_ID, InterfaceUrls::loanStorageUrl, "loans");
  private final AssertingRecordClient loanHistoryClient = new AssertingRecordClient(
    client, TENANT_ID, InterfaceUrls::loanHistoryUrl, "loansHistory");

  private final String firstLoanId = UUID.randomUUID().toString();
  private final String secondLoanId = UUID.randomUUID().toString();

  @Before
  public void beforeEach() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    StorageTestSuite.deleteAll(InterfaceUrls.loanStorageUrl());

    JsonObject loan1 = loansClient.create(new LoanRequestBuilder()
      .withId(UUID.fromString(firstLoanId))
      .withItemId(UUID.randomUUID())
      .withUserId(UUID.randomUUID())
      .checkedOut()
      .create()).getJson();

    JsonObject loan2 = loansClient.create(new LoanRequestBuilder()
      .withId(UUID.fromString(secondLoanId))
      .withItemId(UUID.randomUUID())
      .withUserId(UUID.randomUUID())
      .checkedOut()
      .create()).getJson();

    loansClient.replace(firstLoanId, LoanRequestBuilder.from(loan1).checkedIn());
    loansClient.replace(secondLoanId, LoanRequestBuilder.from(loan2).checkedIn());
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("loan");
    StorageTestSuite.checkForMismatchedIDs("audit_loan");
  }

  @Test
  public void canAnonymizeLoans() throws InterruptedException, ExecutionException,
    TimeoutException, MalformedURLException {

    final var response = anonymizeLoans(firstLoanId, secondLoanId);

    assertThat(response.getAnonymizedLoans(), containsInAnyOrder(firstLoanId, secondLoanId));
    assertThat(loansClient.getById(firstLoanId).getJson(), isAnonymized());
    assertThat(loansClient.getById(secondLoanId).getJson(), isAnonymized());

    // assert that history also anonymized
    assertThat(getLoanHistoryForLoans(),
      // Check out, check in and anonymize entries for two loans
      allOf(iterableWithSize(6), everyItem(LoanHistoryMatchers.isAnonymized())));
  }

  @Test
  public void canNotAnonymizeEmptyList() throws MalformedURLException {
    JsonResponse response = attemptAnonymizeLoans();

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canAnonymizeInvalidAndValidUuids() throws MalformedURLException {
    final String firstNotValidId = "not valid";
    final String secondNotValidId = "null";

    final var response = anonymizeLoans(firstLoanId, secondLoanId,
      firstNotValidId, secondNotValidId);

    assertThat(response.getAnonymizedLoans(), containsInAnyOrder(firstLoanId, secondLoanId));
    assertThat(response.getNotAnonymizedLoans().size(), is(1));
    assertThat(response.getNotAnonymizedLoans().get(0).getReason(), is("invalidLoanIds"));
    assertThat(response.getNotAnonymizedLoans().get(0).getLoanIds(),
      containsInAnyOrder(firstNotValidId, secondNotValidId));
  }

  private AnonymizeStorageLoansResponse anonymizeLoans(String... loanIds) throws MalformedURLException {
    final JsonResponse response = attemptAnonymizeLoans(loanIds);

    assertThat(response.getStatusCode(), is(200));

    return response.getJson().mapTo(AnonymizeStorageLoansResponse.class);
  }

  private JsonResponse attemptAnonymizeLoans(String... loanIds) throws MalformedURLException {
    final var requestBody = new JsonObject()
      .put("loanIds", new JsonArray(List.of(loanIds)));

    final var completed = new CompletableFuture<JsonResponse>();

    client.post(anonymizeLoansURL(), requestBody, TENANT_ID, json(completed));

    return get(completed);
  }

  private Collection<JsonObject> getLoanHistoryForLoans() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    final var query = format("loan.id==(\"%s\" or \"%s\")", firstLoanId, secondLoanId);

    return loanHistoryClient.getMany(query).getRecords();
  }
}
