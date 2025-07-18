package org.folio.rest.api;

import static java.lang.Boolean.TRUE;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertCreateEventForLoan;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertLoanEventCount;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertNoLoanEvent;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRemoveAllEventForLoan;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRemoveEventForLoan;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertUpdateEventForLoan;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isBadRequest;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNotFound;
import static org.folio.rest.support.matchers.LoanMatchers.isClosed;
import static org.folio.rest.support.matchers.LoanMatchers.isOpen;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessageContaining;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.rest.support.matchers.ValidationResponseMatchers.isValidationResponseWhich;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTime.parse;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoansApiTest extends ApiTests {
  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::loanStorageUrl, "loans");

  @Before
  @SneakyThrows
  public void beforeEach() {
    pgClient.execute("TRUNCATE loan")
    .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    FakeKafkaConsumer.removeAllEvents();
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("loan");
  }

  @Test
  public void canCreateALoan()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID itemLocationAtCheckOut = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();
    UUID loanPolicyId = UUID.randomUUID();
    DateTime expectedLostDate = DateTime.now();
    UUID overdueFinePolicyId = UUID.randomUUID();
    UUID lostItemPolicyId = UUID.randomUUID();
    final DateTime claimedReturnedDate = DateTime.now(DateTimeZone.UTC);
    final DateTime agedToLostDate = DateTime.now(DateTimeZone.UTC).minusDays(10);
    DateTime dateLostItemShouldBeBilled = new DateTime(2017, 9, 27, 10, 23, 43, DateTimeZone.UTC);

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withLoanDate(new DateTime(2017, 6, 27, 10, 23, 43, DateTimeZone.UTC))
      .open()
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withDueDate(new DateTime(2017, 7, 27, 10, 23, 43, DateTimeZone.UTC))
      .withItemEffectiveLocationIdAtCheckOut(itemLocationAtCheckOut)
      .withLoanPolicyId(loanPolicyId)
      .withDeclaredLostDate(expectedLostDate)
      .withOverdueFinePolicyId(overdueFinePolicyId)
      .withLostItemPolicyId(lostItemPolicyId)
      .withClaimedReturnedDate(claimedReturnedDate)
      .withAgedToLostDelayedBilling(false, dateLostItemShouldBeBilled, agedToLostDate)
      .create();

    JsonObject loan = loansClient.create(loanRequest).getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxyUserId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-06-27T10:23:43.000Z"));

    assertThat(loan, isOpen());

    assertThat("action is not checked out",
      loan.getString("action"), is("checkedout"));

    assertThat("item status is not checked out",
      loan.getString("itemStatus"), is("Checked out"));

    assertThat("itemEffectiveLocationIdAtCheckOut does not match",
      loan.getString("itemEffectiveLocationIdAtCheckOut"), is(itemLocationAtCheckOut.toString()));

    assertThat("loan policy should be set",
      loan.getString("loanPolicyId"), is(loanPolicyId.toString()));

    assertThat("due date does not match",
      loan.getString("dueDate"), is("2017-07-27T10:23:43.000+00:00"));

    assertThat("recall changed due date should be null",
        loan.getBoolean("dueDateChangedByRecall"), nullValue());

    assertThat("Loan should have a declaredLostDate property", DateTime
      .parse(loansClient.getById(id).getJson().getString("declaredLostDate"))
      .getMillis(), is(expectedLostDate.getMillis()));

    assertThat("Overdue fine policy id should be set",
      loan.getString("overdueFinePolicyId"),
      is(overdueFinePolicyId.toString()));

    assertThat("Lost item policy id should be set",
      loan.getString("lostItemPolicyId"), is(lostItemPolicyId.toString()));

    assertThat(parse(loan.getString("claimedReturnedDate")), is(claimedReturnedDate));

    final JsonObject agedToLostDelayedBilling = loan.getJsonObject("agedToLostDelayedBilling");
    assertThat(agedToLostDelayedBilling.getBoolean("lostItemHasBeenBilled"),
      is(false));
    assertThat(parse(agedToLostDelayedBilling.getString("dateLostItemShouldBeBilled")),
      is(dateLostItemShouldBeBilled));
    assertThat(parse(agedToLostDelayedBilling.getString("agedToLostDate")), is(agedToLostDate));

    assertCreateEventForLoan(loan);
  }

  @Test
  public void canCreateALoanForDcb()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID itemLocationAtCheckOut = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();
    UUID loanPolicyId = UUID.randomUUID();
    DateTime expectedLostDate = DateTime.now();
    UUID overdueFinePolicyId = UUID.randomUUID();
    UUID lostItemPolicyId = UUID.randomUUID();
    final DateTime claimedReturnedDate = DateTime.now(DateTimeZone.UTC);
    final DateTime agedToLostDate = DateTime.now(DateTimeZone.UTC).minusDays(10);
    DateTime dateLostItemShouldBeBilled = new DateTime(2017, 9, 27, 10, 23, 43, DateTimeZone.UTC);

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withLoanDate(new DateTime(2017, 6, 27, 10, 23, 43, DateTimeZone.UTC))
      .open()
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withDueDate(new DateTime(2017, 7, 27, 10, 23, 43, DateTimeZone.UTC))
      .withItemEffectiveLocationIdAtCheckOut(itemLocationAtCheckOut)
      .withLoanPolicyId(loanPolicyId)
      .withDeclaredLostDate(expectedLostDate)
      .withOverdueFinePolicyId(overdueFinePolicyId)
      .withLostItemPolicyId(lostItemPolicyId)
      .withClaimedReturnedDate(claimedReturnedDate)
      .withAgedToLostDelayedBilling(false, dateLostItemShouldBeBilled, agedToLostDate)
      .withIsDcb(true)
      .create();

    JsonObject loan = loansClient.create(loanRequest).getJson();

    assertThat("dcb property should be true ",
      loan.getString("isDcb"), is("true"));
  }

  @Test
  public void canCreateALoanForNonDcb()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID itemLocationAtCheckOut = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();
    UUID loanPolicyId = UUID.randomUUID();
    DateTime expectedLostDate = DateTime.now();
    UUID overdueFinePolicyId = UUID.randomUUID();
    UUID lostItemPolicyId = UUID.randomUUID();
    final DateTime claimedReturnedDate = DateTime.now(DateTimeZone.UTC);
    final DateTime agedToLostDate = DateTime.now(DateTimeZone.UTC).minusDays(10);
    DateTime dateLostItemShouldBeBilled = new DateTime(2017, 9, 27, 10, 23, 43, DateTimeZone.UTC);

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withLoanDate(new DateTime(2017, 6, 27, 10, 23, 43, DateTimeZone.UTC))
      .open()
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withDueDate(new DateTime(2017, 7, 27, 10, 23, 43, DateTimeZone.UTC))
      .withItemEffectiveLocationIdAtCheckOut(itemLocationAtCheckOut)
      .withLoanPolicyId(loanPolicyId)
      .withDeclaredLostDate(expectedLostDate)
      .withOverdueFinePolicyId(overdueFinePolicyId)
      .withLostItemPolicyId(lostItemPolicyId)
      .withClaimedReturnedDate(claimedReturnedDate)
      .withAgedToLostDelayedBilling(false, dateLostItemShouldBeBilled, agedToLostDate)
      .withIsDcb(false)
      .create();

    JsonObject loan = loansClient.create(loanRequest).getJson();

    assertThat("dcb property should be false",
      loan.getString("isDcb"), is("false"));
  }

    @Test
  public void canCreateALoanWithDueDateChangedByRecallSet()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();
    UUID loanPolicyId = UUID.randomUUID();

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withLoanDate(new DateTime(2017, 6, 27, 10, 23, 43, DateTimeZone.UTC))
      .open()
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withDueDate(new DateTime(2017, 7, 27, 10, 23, 43, DateTimeZone.UTC))
      .withLoanPolicyId(loanPolicyId)
      .withDueDateChangedByRecall(TRUE)
      .create();

    JsonObject loan = loansClient.create(loanRequest).getJson();

    assertThat("recall changed due date is not true",
        loan.getBoolean("dueDateChangedByRecall"), is(TRUE));

    assertCreateEventForLoan(loan);
  }

  @Test
  public void canCreateALoanWithoutAnId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();

    JsonObject loanRequest = new LoanRequestBuilder()
      .withNoId()
      .withItemId(itemId)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withLoanDate(new DateTime(2017, 3, 20, 7, 21, 45, DateTimeZone.UTC))
      .open()
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withDueDate(new DateTime(2017, 4, 20, 7, 21, 45, DateTimeZone.UTC))
      .create();

    JsonObject loan = loansClient.create(loanRequest).getJson();

    String newId = loan.getString("id");

    assertThat(newId, is(notNullValue()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxyUserId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-03-20T07:21:45.000Z"));

    assertThat(loan, isOpen());

    assertThat("action is not checked out",
      loan.getString("action"), is("checkedout"));

    assertThat("item status is not checked out",
      loan.getString("itemStatus"), is("Checked out"));

    assertThat("due date does not match",
      loan.getString("dueDate"), is("2017-04-20T07:21:45.000+00:00"));

    assertCreateEventForLoan(loan);
  }

  @Test
  public void canCreateAnAlreadyClosedLoan()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);
    DateTime returnDate = new DateTime(2017, 4, 1, 11, 35, 0, DateTimeZone.UTC);
    DateTime systemReturnDate = new DateTime(2017, 4, 1, 12, 0, 0, DateTimeZone.UTC);

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withReturnDate(returnDate)
      .withSystemReturnDate(systemReturnDate)
      .closed()
      .create();

    JsonObject loan = loansClient.create(loanRequest).getJson();

    assertThat("return date does not match",
      loan.getString("returnDate"), is("2017-04-01T11:35:00.000Z"));

    assertThat("system return date does not match",
      loan.getString("systemReturnDate"), is("2017-04-01T12:00:00.000+00:00"));

    assertCreateEventForLoan(loan);
  }

  @Test
  public void canCreateAnAlreadyClosedLoanWithoutUserId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withNoUserId()
      .closed()
      .create();

    final IndividualResource createLoanResponse = loansClient.create(loanRequest);

    JsonObject loan = createLoanResponse.getJson();

    assertThat(loan, isClosed());

    assertThat("should not have a user ID",
      loan.containsKey("userId"), is(false));

    assertCreateEventForLoan(loan);
  }

  @Test
  public void canCreateALoanAtViaPutToSpecificLocation()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withLoanDate(new DateTime(2017, 2, 27, 21, 14, 43, DateTimeZone.UTC))
      .open()
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withDueDate(new DateTime(2017, 3, 29, 21, 14, 43, DateTimeZone.UTC))
      .create();

    loansClient.createAtSpecificLocation(id, loanRequest);

    JsonObject loan = loansClient.getById(id).getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxyUserId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-02-27T21:14:43.000Z"));

    assertThat(loan, isOpen());

    assertThat("action is not checked out",
      loan.getString("action"), is("checkedout"));

    assertThat("due date does not match",
      loan.getString("dueDate"), is("2017-03-29T21:14:43.000+00:00"));

    assertCreateEventForLoan(loan);
  }

  @Test
  public void canCreateALoanWithOnlyRequiredProperties()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withNoStatus()
      .withNoItemStatus() //Item status is currently optional
      .withLoanDate(new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC))
      .withAction("checkedout")
      .create();

    loansClient.create(loanRequest);

    JsonObject loan = loansClient.getById(id).getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat(loan, isOpen());

    assertCreateEventForLoan(loan);
  }

  @Test
  public void canCreateALoanAtSpecificLocationWithOnlyRequiredProperties()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withNoStatus()
      .withNoItemStatus() //Item status is currently optional
      .withLoanDate(new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC))
      .withAction("checkedout")
      .create();

    loansClient.createAtSpecificLocation(id, loanRequest);

    JsonObject loan = loansClient.getById(id).getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat(loan, isOpen());

    assertCreateEventForLoan(loan);
  }

  @Test
  public void cannotCreateALoanWithoutAction()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject loanRequest = new LoanRequestBuilder().create();

    loanRequest.remove("action");

    JsonResponse response = loansClient.attemptCreate(loanRequest);

    assertThat(response, isValidationResponseWhich(allOf(
      anyOf(hasMessage("must not be null"), hasMessage("darf nicht null sein")),  // any server language
      hasParameter("action", "null"))));

    assertNoLoanEvent(loanRequest.getString("id"));
  }

  @Test
  public void cannotCreateALoanWithInvalidDates()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject loanRequest = new LoanRequestBuilder().create();

    loanRequest
      .put("loanDate", "foo")
      .put("returnDate", "bar");

    JsonResponse response = loansClient.attemptCreate(loanRequest);

    assertThat(String.format("Creating the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(),
      containsString("loan date must be a date time (in RFC3339 format)"));

    assertThat(response.getBody(),
      containsString("return date must be a date time (in RFC3339 format)"));

    assertNoLoanEvent(loanRequest.getString("id"));
  }

  @Test
  public void cannotCreateMultipleOpenLoansForSameItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    JsonObject firstLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .open()
      .create();

    loansClient.create(firstLoanRequest);

    JsonObject firstLoan = loansClient.getById(firstLoanRequest.getString("id")).getJson();

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .open()
      .create();

    JsonResponse response = loansClient.attemptCreate(secondLoanRequest);

    assertThat(response, isValidationResponseWhich(hasMessage(
      "Cannot have more than one open loan for the same item")));

    assertCreateEventForLoan(firstLoan);
    assertNoLoanEvent(secondLoanRequest.getString("id"));
  }

  @Test
  public void cannotCreateMultipleOpenLoansForSameItemViaPutToSpecificLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    JsonObject firstLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .open()
      .create();

    loansClient.create(firstLoanRequest);

    JsonObject firstLoan = loansClient.getById(firstLoanRequest.getString("id")).getJson();

    UUID secondLoanId = UUID.randomUUID();

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withId(secondLoanId)
      .withItemId(itemId)
      .open()
      .create();

    JsonResponse createResponse = loansClient.attemptCreateOrReplace(
      secondLoanId.toString(), secondLoanRequest);

    assertThat(createResponse, isValidationResponseWhich(hasMessage(
      "Cannot have more than one open loan for the same item")));

    assertCreateEventForLoan(firstLoan);
    assertNoLoanEvent(secondLoanId.toString());
  }

  @Test
  public void canCreateOpenLoanWhenClosedLoansForSameItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    JsonObject closedLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .closed()
      .create();

    loansClient.create(closedLoanRequest);

    JsonObject closedLoan = loansClient.getById(closedLoanRequest.getString("id")).getJson();

    JsonObject openLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .open()
      .create();

    loansClient.create(openLoanRequest);

    JsonObject openLoan = loansClient.getById(openLoanRequest.getString("id")).getJson();

    assertCreateEventForLoan(closedLoan);
    assertCreateEventForLoan(openLoan);
  }

  @Test
  public void canCreateMultipleOpenLoansForDifferentItems()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstItemId = UUID.randomUUID();
    UUID secondItemId = UUID.randomUUID();

    JsonObject firstLoanRequest = new LoanRequestBuilder()
      .withItemId(firstItemId)
      .open()
      .create();

    loansClient.create(firstLoanRequest);

    JsonObject firstLoan = loansClient.getById(firstLoanRequest.getString("id")).getJson();

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withItemId(secondItemId)
      .open()
      .create();

    loansClient.create(secondLoanRequest);

    JsonObject secondLoan = loansClient.getById(secondLoanRequest.getString("id")).getJson();

    assertCreateEventForLoan(firstLoan);
    assertCreateEventForLoan(secondLoan);
  }

  @Test
  public void canCreateMultipleClosedLoansForSameItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    JsonObject firstLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .closed()
      .create();

    loansClient.create(firstLoanRequest);
    JsonObject firstLoan = loansClient.getById(firstLoanRequest.getString("id")).getJson();

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .closed()
      .create();

    loansClient.create(secondLoanRequest);
    JsonObject secondLoan = loansClient.getById(secondLoanRequest.getString("id")).getJson();

    assertCreateEventForLoan(firstLoan);
    assertCreateEventForLoan(secondLoan);
  }

  @Test
  public void canCreateClosedLoanWhenOpenLoanForDifferentItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstItemId = UUID.randomUUID();
    UUID secondItemId = UUID.randomUUID();

    JsonObject firstLoanRequest = new LoanRequestBuilder()
      .withItemId(firstItemId)
      .open()
      .create();

    loansClient.create(firstLoanRequest);
    JsonObject firstLoan = loansClient.getById(firstLoanRequest.getString("id")).getJson();

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withItemId(secondItemId)
      .closed()
      .create();

    loansClient.create(secondLoanRequest);
    JsonObject secondLoan = loansClient.getById(secondLoanRequest.getString("id")).getJson();

    assertCreateEventForLoan(firstLoan);
    assertCreateEventForLoan(secondLoan);
  }

  @Test
  public void canGetALoanById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();

    JsonObject loanRequest = new LoanRequestBuilder()
      .withId(id)
      .withItemId(itemId)
      .withUserId(userId)
      .withProxyUserId(proxyUserId)
      .withLoanDate(new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC))
      .open()
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .create();

    loansClient.create(loanRequest);

    JsonObject loan = loansClient.getById(id).getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxyUserId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2016-10-15T08:26:53.000Z"));

    assertThat("item status is not checked out",
      loan.getString("itemStatus"), is("Checked out"));

    assertThat(loan, isOpen());
  }

  @Test
  public void cannotGetALoanForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonResponse getResponse = loansClient.attemptGetById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canCloseALoanByReturningTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);
    final UUID userId = UUID.randomUUID();

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withUserId(userId)
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plus(Period.days(14)))
      .create());

    JsonObject createdLoan = loan.copyJson();

    LoanRequestBuilder returnedLoan = LoanRequestBuilder.from(createdLoan)
      .closed()
      .withAction("checkedin")
      .withItemStatus("Available")
      .withReturnDate(new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC));

    loansClient.replace(loan.getId(), returnedLoan);

    JsonObject updatedLoan = loansClient.getById(UUID.fromString(loan.getId()))
      .getJson();

    assertThat(updatedLoan.getString("userId"), is(userId.toString()));

    assertThat(updatedLoan.getString("returnDate"),
      is("2017-03-05T14:23:41.000Z"));

    assertThat(updatedLoan, isClosed());

    assertThat("item status is not available",
      updatedLoan.getString("itemStatus"), is("Available"));

    assertThat("action is not checkedin",
      updatedLoan.getString("action"), is("checkedin"));

    assertCreateEventForLoan(createdLoan);
    assertUpdateEventForLoan(createdLoan, updatedLoan);
  }

  @Test
  public void canRemoveUserIdFromClosedLoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);
    final UUID loanId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withUserId(userId)
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plus(Period.days(14)))
      .open()
      .create());

    final LoanRequestBuilder closedLoanRequest = LoanRequestBuilder
      .from(loan.getJson())
      .closed();

    loansClient.replace(loanId.toString(), closedLoanRequest);

    final IndividualResource closedLoan = loansClient.getById(loanId);

    final LoanRequestBuilder anonymisedLoanRequest = LoanRequestBuilder
      .from(closedLoan.getJson())
      .withNoUserId();

    loansClient.replace(loanId.toString(), anonymisedLoanRequest);

    final IndividualResource anonymisedLoan = loansClient.getById(loanId);

    assertThat(anonymisedLoan.getJson(), isClosed());

    assertThat("Should not have a user ID",
      anonymisedLoan.getJson().containsKey("userId"), is(false));

    assertCreateEventForLoan(loan.getJson());
    assertUpdateEventForLoan(closedLoan.getJson(), anonymisedLoan.getJson());
  }

  @Test
  public void cannotCreateOpenLoanWithoutUserId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final UUID loanId = UUID.randomUUID();
    final LoanRequestBuilder loanRequest = new LoanRequestBuilder()
      .withId(loanId)
      .open()
      .withNoUserId();

    final JsonResponse putResponse = loansClient.attemptCreate(loanRequest);

    assertThat(putResponse, isValidationResponseWhich(
      hasMessage("Open loan must have a user ID")));

    assertNoLoanEvent(loanId.toString());
  }

  @Test
  public void cannotCreateOpenLoanWithoutUserIdViaPut()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final UUID loanId = UUID.randomUUID();

    final LoanRequestBuilder loanRequest = new LoanRequestBuilder()
      .withId(loanId)
      .open()
      .withNoUserId();

    final JsonResponse putResponse = loansClient.attemptCreateOrReplace(
      loanId.toString(), loanRequest);

    assertThat(putResponse, isValidationResponseWhich(
      hasMessage("Open loan must have a user ID")));

    assertNoLoanEvent(loanId.toString());
  }

  @Test
  public void cannotRemoveUserIdFromOpenLoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final UUID loanId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withId(loanId)
      .withUserId(userId)
      .open()
      .create());

    final LoanRequestBuilder loanRequestWithoutId = LoanRequestBuilder
      .from(loan.getJson())
      .withNoUserId();

    final JsonResponse putResponse = loansClient.attemptCreateOrReplace(
      loanId.toString(), loanRequestWithoutId);

    assertThat(putResponse, isValidationResponseWhich(
      hasMessage("Open loan must have a user ID")));

    assertCreateEventForLoan(loan.getJson());
    assertLoanEventCount(loanId.toString(), 1);
  }

  @Test
  public void canRenewALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2017, DateTimeConstants.MARCH, 1, 13, 25, 46, DateTimeZone.UTC);

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plus(Period.days(14)))
      .create());

    JsonObject createdLoan = loan.getJson();

    LoanRequestBuilder returnedLoan = LoanRequestBuilder.from(createdLoan)
      .withDueDate(new DateTime(2017, DateTimeConstants.MARCH, 30, 13, 25, 46, DateTimeZone.UTC))
      .withAction("renewed")
      .withActionComment("test action comment")
      .withItemStatus("Checked out")
      .withRenewalCount(1);

    loansClient.replace(loan.getId(), returnedLoan);

    JsonObject updatedLoan = loansClient.getById(UUID.fromString(loan.getId()))
      .getJson();

    assertThat(updatedLoan.getString("dueDate"),
      is("2017-03-30T13:25:46.000+00:00"));

    assertThat(updatedLoan, isOpen());

    assertThat("action is not renewed",
      updatedLoan.getString("action"), is("renewed"));

    assertThat("action comment is incorrect",
      updatedLoan.getString("actionComment"), is("test action comment"));

    assertThat("renewal count is not 1",
      updatedLoan.getInteger("renewalCount"), is(1));

    assertThat("item status is not checked out",
      updatedLoan.getString("itemStatus"), is("Checked out"));

    assertCreateEventForLoan(createdLoan);
    assertUpdateEventForLoan(createdLoan, updatedLoan);
  }

  @Test
  public void omittedStatusFromReplacedLoanDefaultsToOpen()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .open()
      .create());

    JsonObject createdLoan = loan.copyJson();
    LoanRequestBuilder returnedLoan = LoanRequestBuilder.from(createdLoan)
      .withNoStatus();

    loansClient.replace(loan.getId(), returnedLoan);

    JsonObject updatedLoan = loansClient.getById(UUID.fromString(loan.getId()))
      .getJson();

    assertThat(updatedLoan, isOpen());

    assertCreateEventForLoan(createdLoan);
    assertUpdateEventForLoan(createdLoan, updatedLoan);
  }

  @Test
  public void cannotReopenLoanWhenOpenLoanForSameItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    JsonObject openLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .open()
      .create();

    IndividualResource openLoan = loansClient.create(openLoanRequest);

    JsonObject closedLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .closed()
      .create();

    IndividualResource closedLoan = loansClient.create(closedLoanRequest);

    JsonObject closed = closedLoan.getJson();
    LoanRequestBuilder reopenLoanRequest = LoanRequestBuilder.from(closed)
      .open();

    JsonResponse replaceResponse = loansClient.attemptCreateOrReplace(
      closedLoan.getId(), reopenLoanRequest.create());

    assertThat(replaceResponse, isValidationResponseWhich(hasMessage(
      "Cannot have more than one open loan for the same item")));

    assertCreateEventForLoan(openLoan.getJson());
    assertCreateEventForLoan(closed);
    assertLoanEventCount(closed.getString("id"), 1);
  }

  @Test
  public void cannotUpdateALoanWithInvalidDates()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource loan = loansClient.create(loanRequest());

    JsonObject changedLoan = loan.copyJson();

    changedLoan.put("loanDate", "bar");
    changedLoan.put("returnDate", "foo");

    final JsonResponse response = loansClient.attemptCreateOrReplace(
      loan.getId(), changedLoan);

    assertThat(response, isBadRequest());

    //TODO: Convert these to validation responses
    assertThat(response.getBody(),
      containsString("loan date must be a date time (in RFC3339 format)"));

    assertThat(response.getBody(),
      containsString("return date must be a date time (in RFC3339 format)"));

    JsonObject created = loan.getJson();
    assertCreateEventForLoan(created);
    assertLoanEventCount(created.getString("id"), 1);
  }

  @Test
  @Ignore("Should conditional field validation be done in a storage module?")
  public void returnDateIsMandatoryForClosedLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    IndividualResource loan = loansClient.create(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plus(Period.days(14)))
      .create());

    LoanRequestBuilder returnedLoan = LoanRequestBuilder.from(loan.copyJson())
      .closed();

    CompletableFuture<TextResponse> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.loanStorageUrl(String.format("/%s", loan.getId())),
      returnedLoan.create(), StorageTestSuite.TENANT_ID,
      ResponseHandler.text(putCompleted));

    TextResponse response = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should have failed to update loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(),
      containsString("return date is mandatory to close a loan"));
  }

  @Test
  public void canPageLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    loansClient.create(loanRequest());
    loansClient.create(loanRequest());
    loansClient.create(loanRequest());
    loansClient.create(loanRequest());
    loansClient.create(loanRequest());
    loansClient.create(loanRequest());
    loansClient.create(loanRequest());

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.loanStorageUrl() + "?limit=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(InterfaceUrls.loanStorageUrl() + "?limit=4&offset=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    JsonResponse firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get first page of loans: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get second page of loans: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageLoans = firstPage.getJsonArray("loans");
    JsonArray secondPageLoans = secondPage.getJsonArray("loans");

    assertThat(firstPageLoans.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(7));

    assertThat(secondPageLoans.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(7));
  }

  @Test
  public void canSearchByUserId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();

    String queryTemplate = InterfaceUrls.loanStorageUrl() + "?query=userId='%s'";

    loansClient.create(new LoanRequestBuilder().withUserId(firstUserId).create());
    loansClient.create(new LoanRequestBuilder().withUserId(firstUserId).create());
    loansClient.create(new LoanRequestBuilder().withUserId(firstUserId).create());
    loansClient.create(new LoanRequestBuilder().withUserId(firstUserId).create());

    loansClient.create(new LoanRequestBuilder().withUserId(secondUserId).create());
    loansClient.create(new LoanRequestBuilder().withUserId(secondUserId).create());
    loansClient.create(new LoanRequestBuilder().withUserId(secondUserId).create());

    CompletableFuture<JsonResponse> firstUserSearchCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondUserSeatchCompleted = new CompletableFuture<>();

    client.get(String.format(queryTemplate, firstUserId), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstUserSearchCompleted));

    client.get(String.format(queryTemplate, secondUserId), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondUserSeatchCompleted));

    JsonResponse firstPageResponse = firstUserSearchCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondUserSeatchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get loans for first user: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get loans for second user: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageLoans = firstPage.getJsonArray("loans");
    JsonArray secondPageLoans = secondPage.getJsonArray("loans");

    assertThat(firstPageLoans.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(4));

    assertThat(secondPageLoans.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(3));
  }

  @Test
  public void canFilterByLoanStatus()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID userId = UUID.randomUUID();

    String queryTemplate = "query=userId=%s+and+status.name=%s";

    loansClient.create(loanRequest(userId, "Open"));
    loansClient.create(loanRequest(userId, "Open"));
    loansClient.create(loanRequest(userId, "Closed"));
    loansClient.create(loanRequest(userId, "Closed"));
    loansClient.create(loanRequest(userId, "Closed"));
    loansClient.create(loanRequest(userId, "Closed"));

    CompletableFuture<JsonResponse> openSearchComppleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> closedSearchCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.loanStorageUrl(), String.format(queryTemplate, userId, "Open"),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(openSearchComppleted));

    client.get(InterfaceUrls.loanStorageUrl(), String.format(queryTemplate, userId, "Closed"),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(closedSearchCompleted));

    JsonResponse openLoansResponse = openSearchComppleted.get(5, TimeUnit.SECONDS);
    JsonResponse closedLoansResponse = closedSearchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get open loans: %s",
      openLoansResponse.getBody()),
      openLoansResponse.getStatusCode(), is(200));

    assertThat(String.format("Failed to get closed loans: %s",
      closedLoansResponse.getBody()),
      closedLoansResponse.getStatusCode(), is(200));

    JsonObject openLoans = openLoansResponse.getJson();
    JsonObject closedLoans = closedLoansResponse.getJson();

    JsonArray firstPageLoans = openLoans.getJsonArray("loans");
    JsonArray secondPageLoans = closedLoans.getJsonArray("loans");

    assertThat(firstPageLoans.size(), is(2));
    assertThat(openLoans.getInteger("totalRecords"), is(2));

    assertThat(secondPageLoans.size(), is(4));
    assertThat(closedLoans.getInteger("totalRecords"), is(4));
  }

  @Test
  public void shouldPopulateChangeMetadataProperties()
    throws Exception {

    UUID userId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();

    JsonObject j1 = new JsonObject()
      .put("id", id1.toString())
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("action", "checkedout")
      .put("loanDate", DateTime.parse("2017-03-06T16:04:43.000+02:00",
        ISODateTimeFormat.dateTime()).toString())
      .put("status", new JsonObject().put("name", "Closed"));

    JsonObject j2 = new JsonObject()
      .put("id", id2.toString())
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("action", "renewal")
      .put("loanDate", DateTime.parse("2017-03-06T16:05:43.000+02:00",
        ISODateTimeFormat.dateTime()).toString())
      .put("status", new JsonObject().put("name", "Opened"));

    JsonObject j3 = new JsonObject()
      .put("id", id3.toString())
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("action", "renewal")
      .put("loanDate", DateTime.parse("2017-03-06T16:05:43.000+02:00",
        ISODateTimeFormat.dateTime()).toString())
      .put("status", new JsonObject().put("name", "Opened"));
    Metadata md = new Metadata();
    md.setCreatedByUserId("af23adf0-61ba-4887-bf82-956c4aae2260");
    md.setUpdatedByUserId("af23adf0-61ba-4887-bf82-956c4aae2260");

    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+0000");
    df.setTimeZone(tz);
    Date d = new Date();
    String nowAsISO = df.format(d);

    md.setCreatedDate(d);
    md.setUpdatedDate(d);
    j3.put("metadata", JsonObject.mapFrom(md));

    CompletableFuture<JsonResponse> create1 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> get1 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> create2 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> get2 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> create3 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> get3 = new CompletableFuture<>();

    ///////////////post loan//////////////////////
    client.post(InterfaceUrls.loanStorageUrl(), j1, StorageTestSuite.TENANT_ID,
      "af23adf0-61ba-4887-bf82-956c4aae2260", ResponseHandler.json(create1));

    create1.get(5, TimeUnit.SECONDS);

    //////////////get loan/////////////////////
    client.get(InterfaceUrls.loanStorageUrl("/" + id1.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get1));

    JsonResponse response2 = get1.get(5, TimeUnit.SECONDS);

    assertThat("Metadata section not populated correctly " + id1.toString(),
      response2.getJson().getJsonObject("metadata").getString("createdByUserId"), is("af23adf0-61ba-4887-bf82-956c4aae2260"));

    ///////////////post loan//////////////////////
    client.post(InterfaceUrls.loanStorageUrl(), j2, StorageTestSuite.TENANT_ID, null,
      ResponseHandler.json(create2));

    create2.get(5, TimeUnit.SECONDS);

    //////////////get loan/////////////////////
    client.get(InterfaceUrls.loanStorageUrl("/" + id2.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get2));

    JsonResponse response4 = get2.get(5, TimeUnit.SECONDS);

    assertThat("Metadata section not populated correctly " + id2.toString(),
      response4.getJson().getJsonObject("metadata"), not(nullValue()));

    assertThat("Metadata section requires createdDate property " + id2.toString(),
      response4.getJson().getJsonObject("metadata").containsKey("createdDate"), not(nullValue()));

    ///////////////post loan//////////////////////
    client.post(InterfaceUrls.loanStorageUrl(), j3, StorageTestSuite.TENANT_ID,
      "af23adf0-61ba-4887-bf82-956c4aae2260", ResponseHandler.json(create3));

    create3.get(5, TimeUnit.SECONDS);

    //////////////get loan/////////////////////
    client.get(InterfaceUrls.loanStorageUrl("/" + id3.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get3));

    JsonResponse response6 = get3.get(5, TimeUnit.SECONDS);

    // server should overwrite the field so should not be equal to what was passed in
    assertThat("Metadata section not populated correctly " + id3.toString(),
      response6.getJson().getJsonObject("metadata").getString("createdDate"), not(nowAsISO));
  }

  @Test
  public void canDeleteALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    IndividualResource loanResouce = loansClient.create(new LoanRequestBuilder().withId(id).create());

    loansClient.deleteById(id);

    JsonResponse getResponse = loansClient.attemptGetById(id);

    assertThat(getResponse, isNotFound());

    JsonObject loan = loanResouce.getJson();
    assertCreateEventForLoan(loan);
    assertRemoveEventForLoan(loan);
  }

  @Test
  @SneakyThrows
  public void canDeleteAllLoans() {
    loansClient.create(new LoanRequestBuilder().create());
    loansClient.create(new LoanRequestBuilder().create());

    loansClient.deleteByCql("cql.allRecords=1");

    assertThat(loansClient.getAll().getRecords(), is(empty()));
    assertRemoveAllEventForLoan();
  }

  @Test
  @SneakyThrows
  public void canDeleteByCql() {
    var loanId1 = UUID.randomUUID();
    var loanId2 = UUID.randomUUID();
    var loanId3 = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var loan1 = loansClient.create(new LoanRequestBuilder().withId(loanId1).withUserId(userId).create());
    loansClient.create(new LoanRequestBuilder().withId(loanId2).create());
    var loan3 = loansClient.create(new LoanRequestBuilder().withId(loanId3).withUserId(userId).create());

    loansClient.deleteByCql("userId==" + userId);

    assertThat(loansClient.attemptGetById(loanId1).getStatusCode(), is(404));
    assertThat(loansClient.attemptGetById(loanId3).getStatusCode(), is(404));
    assertRemoveEventForLoan(loan1.getJson());
    assertRemoveEventForLoan(loan3.getJson());
    loansClient.getById(loanId2);
  }

  @Test
  public void cannotDeleteWithoutCql() {
    var response = loansClient.attemptDelete();
    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  public void cannotDeleteByInvalidCql() {
    var response = loansClient.attemptDeleteByCql(")");
    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInLoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    JsonObject requestWithAdditionalProperty = new LoanRequestBuilder()
      .withId(id)
      .create();

    requestWithAdditionalProperty.put("somethingAdditional", "foo");

    JsonResponse response = loansClient.attemptCreate(
      requestWithAdditionalProperty);

    assertThat(response, isValidationResponseWhich(
      hasMessageContaining("Unrecognized field")));

    assertNoLoanEvent(id.toString());
  }

  @Test
  public void cannotProvideAdditionalPropertiesInLoanStatus()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    JsonObject requestWithAdditionalProperty = new LoanRequestBuilder()
      .withId(id)
      .create();

    requestWithAdditionalProperty.getJsonObject("status")
      .put("somethingAdditional", "foo");

    JsonResponse response = loansClient.attemptCreate(
      requestWithAdditionalProperty);

    assertThat(response, isValidationResponseWhich(
      hasMessageContaining("Unrecognized field")));

    assertNoLoanEvent(id.toString());
  }

  @Test
  public void canSearchByLoanStatus() throws Exception {
    final IndividualResource openLoan = loansClient.create(
      new LoanRequestBuilder().checkedOut());

    loansClient.create(new LoanRequestBuilder().checkedIn());

    final List<String> openLoans = loansClient.getMany("status.name==Open")
      .getRecords().stream()
      .map(json -> json.getString("id"))
      .collect(Collectors.toList());

    assertThat(openLoans, hasSize(1));
    assertThat(openLoans, hasItem(openLoan.getId()));
  }

  @Test
  public void canSearchByLoanItemStatus() throws Exception {
    final IndividualResource agedToLostLoan = loansClient.create(
      new LoanRequestBuilder().agedToLost());

    loansClient.create(new LoanRequestBuilder().lostAndPaid());

    final List<String> agedToLostLoans = loansClient.getMany("itemStatus==Aged to lost")
      .getRecords().stream()
      .map(json -> json.getString("id"))
      .collect(Collectors.toList());

    assertThat(agedToLostLoans, hasSize(1));
    assertThat(agedToLostLoans, hasItem(agedToLostLoan.getId()));
  }

  @Test
  public void canSearchByLoanAgedToLostItemHasBeenBilled() throws Exception {
    final IndividualResource billedLoan = loansClient.create(
      new LoanRequestBuilder()
        .withAgedToLostDelayedBilling(true, DateTime.now(), DateTime.now()));

    loansClient.create(new LoanRequestBuilder()
        .withAgedToLostDelayedBilling(false, DateTime.now(), DateTime.now()));

    final List<String> billedLoans = loansClient.getMany(
      "agedToLostDelayedBilling.lostItemHasBeenBilled==true")
      .getRecords().stream()
      .map(json -> json.getString("id"))
      .collect(Collectors.toList());

    assertThat(billedLoans, hasSize(1));
    assertThat(billedLoans, hasItem(billedLoan.getId()));
  }

  @Test
  public void canSearchByLoanAgedToLostBillingDate() throws Exception {
    final DateTime today = DateTime.now();
    final DateTime yesterday = today.minusDays(1);
    final DateTime tomorrow = today.plusDays(1);

    loansClient.create(new LoanRequestBuilder()
        .withAgedToLostDelayedBilling(true, yesterday, DateTime.now()));

    final IndividualResource loanToBillTomorrow = loansClient.create(
      new LoanRequestBuilder().withAgedToLostDelayedBilling(false, tomorrow, DateTime.now()));

    final List<String> filteredLoans = loansClient.getMany(
      String.format("agedToLostDelayedBilling.dateLostItemShouldBeBilled > \"%s\"", today))
      .getRecords().stream()
      .map(json -> json.getString("id"))
      .collect(Collectors.toList());

    assertThat(filteredLoans, hasSize(1));
    assertThat(filteredLoans, hasItem(loanToBillTomorrow.getId()));
  }

  private JsonObject loanRequest() {
    return new LoanRequestBuilder().create();
  }

  private JsonObject loanRequest(UUID userId, String statusName) {
    return new LoanRequestBuilder()
      .withUserId(userId)
      .withStatus(statusName)
      .create();
  }
}
