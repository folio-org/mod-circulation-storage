package org.folio.rest.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.MetaData;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.*;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.*;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessgeContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoansApiTest extends ApiTests {
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
  public void canCreateALoan()
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
      .withStatus("Open")
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withdueDate(new DateTime(2017, 7, 27, 10, 23, 43, DateTimeZone.UTC))
      .withLoanPolicyId(loanPolicyId)
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject loan = response.getJson();

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

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checked out",
      loan.getString("action"), is("checkedout"));

    assertThat("item status is not checked out",
      loan.getString("itemStatus"), is("Checked out"));

    assertThat("loan policy should be set",
      loan.getString("loanPolicyId"), is(loanPolicyId.toString()));

    //The RAML-Module-Builder converts all date-time formatted strings to UTC
    //and presents the offset as +0000 (which is ISO8601 compatible, but not RFC3339)
    assertThat("due date does not match",
      loan.getString("dueDate"), is("2017-07-27T10:23:43.000+0000"));
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
      .withStatus("Open")
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withdueDate(new DateTime(2017, 4, 20, 7, 21, 45, DateTimeZone.UTC))
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject loan = response.getJson();

    String newId = loan.getString("id");

    Assert.assertThat(newId, is(notNullValue()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(proxyUserId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));

    assertThat("loan date does not match",
      loan.getString("loanDate"), is("2017-03-20T07:21:45.000Z"));

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checked out",
      loan.getString("action"), is("checkedout"));

    assertThat("item status is not checked out",
      loan.getString("itemStatus"), is("Checked out"));

    //The RAML-Module-Builder converts all date-time formatted strings to UTC
    //and presents the offset as +0000 (which is ISO8601 compatible, but not RFC3339)
    assertThat("due date does not match",
      loan.getString("dueDate"), is("2017-04-20T07:21:45.000+0000"));
  }

  @Test
  public void canCreateALoanAtASpecificLocation()
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
      .withStatus("Open")
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withdueDate(new DateTime(2017, 3, 29, 21, 14, 43, DateTimeZone.UTC))
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.put(loanStorageUrl(String.format("/%s", id.toString())), loanRequest,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

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

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not checked out",
      loan.getString("action"), is("checkedout"));

    //The RAML-Module-Builder converts all date-time formatted strings to UTC
    //and presents the offset as +0000 (which is ISO8601 compatible, but not RFC3339)
    assertThat("due date does not match",
      loan.getString("dueDate"), is("2017-03-29T21:14:43.000+0000"));
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
      .withNoStatus() //Status is currently optional, as it is defaulted to Open
      .withNoItemStatus() //Item status is currently optional
      .withLoanDate(new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC))
      .withAction("checkedout")
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.put(loanStorageUrl(String.format("/%s", id.toString())), loanRequest,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));
  }

  @Test
  public void cannotCreateALoanWithoutAction()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject loanRequest = new LoanRequestBuilder().create();

    loanRequest.remove("action");

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));
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

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(),
      containsString("loan date must be a date time (in RFC3339 format)"));

    assertThat(response.getBody(),
      containsString("return date must be a date time (in RFC3339 format)"));
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
      .withStatus("Open")
      .create();

    createLoan(firstLoanRequest);

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .withStatus("Open")
      .create();

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), secondLoanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getErrors(),
      hasSoleMessgeContaining("Cannot have more than one open loan for the same item"));
  }

  @Test
  public void cannotCreateMultipleOpenLoansAtSpecificLocationForSameItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID itemId = UUID.randomUUID();

    JsonObject firstLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .withStatus("Open")
      .create();

    createLoan(firstLoanRequest);

    UUID secondLoanId = UUID.randomUUID();

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withId(secondLoanId)
      .withItemId(itemId)
      .withStatus("Open")
      .create();

    CompletableFuture<JsonErrorResponse> secondCreateCompleted = new CompletableFuture<>();

    client.put(loanStorageUrl(String.format("/%s", secondLoanId.toString())), secondLoanRequest,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(secondCreateCompleted));

    JsonErrorResponse response = secondCreateCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getErrors(),
      hasSoleMessgeContaining("Cannot have more than one open loan for the same item"));
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
      .withStatus("Closed")
      .create();

    createLoan(closedLoanRequest);

    JsonObject openLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .withStatus("Open")
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), openLoanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should succeed: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
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
      .withStatus("Open")
      .create();

    createLoan(firstLoanRequest);

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withItemId(secondItemId)
      .withStatus("Open")
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), secondLoanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should succeed: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
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
      .withStatus("Closed")
      .create();

    createLoan(firstLoanRequest);

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .withStatus("Closed")
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), secondLoanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should succeed: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
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
      .withStatus("Open")
      .create();

    createLoan(firstLoanRequest);

    JsonObject secondLoanRequest = new LoanRequestBuilder()
      .withItemId(secondItemId)
      .withStatus("Closed")
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), secondLoanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should succeed: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
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
      .withStatus("Open")
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .create();

    createLoan(loanRequest);

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

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

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));
  }

  @Test
  public void cannotGetALoanForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonResponse getResponse = getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canCompleteALoanByReturningTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    IndividualResource loan = createLoan(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withdueDate(loanDate.plus(Period.days(14)))
      .create());

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("action", "checkedin")
      .put("itemStatus", "Available")
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC)
      .toString(ISODateTimeFormat.dateTime()));

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture<>();

    client.put(loanStorageUrl(String.format("/%s", loan.getId())), returnedLoan,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(putCompleted));

    JsonResponse putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse updatedLoanResponse = getById(UUID.fromString(loan.getId()));

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat(updatedLoan.getString("returnDate"),
      is("2017-03-05T14:23:41.000Z"));

    assertThat("status is not closed",
      updatedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status is not available",
      updatedLoan.getString("itemStatus"), is("Available"));

    assertThat("action is not checkedin",
      updatedLoan.getString("action"), is("checkedin"));
  }

  @Test
  public void canRenewALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    IndividualResource loan = createLoan(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withdueDate(loanDate.plus(Period.days(14)))
      .create());

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("dueDate", new DateTime(2017, 3, 30, 13, 25, 46, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()))
      .put("action", "renewed")
      .put("itemStatus", "Checked out")
      .put("renewalCount", 1);

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture<>();

    client.put(loanStorageUrl(String.format("/%s", loan.getId())), returnedLoan,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(putCompleted));

    JsonResponse putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse updatedLoanResponse = getById(UUID.fromString(loan.getId()));

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    //The RAML-Module-Builder converts all date-time formatted strings to UTC
    //and presents the offset as +0000 (which is ISO8601 compatible, but not RFC3339)
    assertThat(updatedLoan.getString("dueDate"),
      is("2017-03-30T13:25:46.000+0000"));

    assertThat("status is not open",
      updatedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not renewed",
      updatedLoan.getString("action"), is("renewed"));

    assertThat("renewal count is not 1",
      updatedLoan.getInteger("renewalCount"), is(1));

    assertThat("item status is not checked out",
      updatedLoan.getString("itemStatus"), is("Checked out"));
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
      .withStatus("Open")
      .create();

    createLoan(openLoanRequest);

    JsonObject closedLoanRequest = new LoanRequestBuilder()
      .withItemId(itemId)
      .withStatus("Closed")
      .create();

    IndividualResource closedLoan = createLoan(closedLoanRequest);

    JsonObject reopenLoanRequest = closedLoan.copyJson()
      .put("status", new JsonObject().put("name", "Open"));

    CompletableFuture<JsonErrorResponse> reopenRequestCompleted = new CompletableFuture<>();

    client.put(loanStorageUrl(String.format("/%s", closedLoan.getId())), reopenLoanRequest,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(reopenRequestCompleted));

    JsonErrorResponse response = reopenRequestCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Re-opening the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    assertThat(response.getErrors(),
      hasSoleMessgeContaining("Cannot have more than one open loan for the same item"));
  }

  @Test
  public void cannotUpdateALoanWithInvalidDates()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource loan = createLoan(loanRequest());

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan.put("loanDate", "bar");
    returnedLoan.put("returnDate", "foo");

    CompletableFuture<TextResponse> putCompleted = new CompletableFuture<>();

    client.put(loanStorageUrl(String.format("/%s", loan.getId())), returnedLoan,
      StorageTestSuite.TENANT_ID, ResponseHandler.text(putCompleted));

    TextResponse response = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should have failed to update loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(),
      containsString("loan date must be a date time (in RFC3339 format)"));

    assertThat(response.getBody(),
      containsString("return date must be a date time (in RFC3339 format)"));
  }

  @Test
  @Ignore("Should conditional field validation be done in a storage module?")
  public void returnDateIsMandatoryForClosedLoans()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    IndividualResource loan = createLoan(new LoanRequestBuilder()
      .withLoanDate(loanDate)
      .withdueDate(loanDate.plus(Period.days(14)))
      .create());

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"));

    CompletableFuture<TextResponse> putCompleted = new CompletableFuture<>();

    client.put(loanStorageUrl(String.format("/%s", loan.getId())), returnedLoan,
      StorageTestSuite.TENANT_ID, ResponseHandler.text(putCompleted));

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

    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture<>();

    client.get(loanStorageUrl() + "?limit=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(loanStorageUrl() + "?limit=4&offset=4", StorageTestSuite.TENANT_ID,
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

    String queryTemplate = loanStorageUrl() + "?query=userId=\"%s\"";

    createLoan(new LoanRequestBuilder().withUserId(firstUserId).create());
    createLoan(new LoanRequestBuilder().withUserId(firstUserId).create());
    createLoan(new LoanRequestBuilder().withUserId(firstUserId).create());
    createLoan(new LoanRequestBuilder().withUserId(firstUserId).create());

    createLoan(new LoanRequestBuilder().withUserId(secondUserId).create());
    createLoan(new LoanRequestBuilder().withUserId(secondUserId).create());
    createLoan(new LoanRequestBuilder().withUserId(secondUserId).create());

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

    String queryTemplate = "query=userId=\"%s\"+and+status.name=\"%s\"";

    createLoan(loanRequest(userId, "Open"));
    createLoan(loanRequest(userId, "Open"));
    createLoan(loanRequest(userId, "Closed"));
    createLoan(loanRequest(userId, "Closed"));
    createLoan(loanRequest(userId, "Closed"));
    createLoan(loanRequest(userId, "Closed"));

    CompletableFuture<JsonResponse> openSearchComppleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> closedSearchCompleted = new CompletableFuture<>();

    client.get(loanStorageUrl(), String.format(queryTemplate, userId, "Open"),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(openSearchComppleted));

    client.get(loanStorageUrl(), String.format(queryTemplate, userId, "Closed"),
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
  public void metaDataPopulated()
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
    MetaData md = new MetaData();
    md.setCreatedByUserId("af23adf0-61ba-4887-bf82-956c4aae2260");
    md.setUpdatedByUserId("af23adf0-61ba-4887-bf82-956c4aae2260");

    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+0000");
    df.setTimeZone(tz);
    Date d = new Date();
    String nowAsISO = df.format(d);

    md.setCreatedDate(d);
    md.setUpdatedDate(d);
    j3.put("metaData", new JsonObject(PostgresClient.pojo2json(md)));

    CompletableFuture<JsonResponse> create1 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> get1    = new CompletableFuture<>();
    CompletableFuture<JsonResponse> create2 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> get2    = new CompletableFuture<>();
    CompletableFuture<JsonResponse> create3 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> get3    = new CompletableFuture<>();

    ///////////////post loan//////////////////////
    client.post(loanStorageUrl(), j1, StorageTestSuite.TENANT_ID,
      "af23adf0-61ba-4887-bf82-956c4aae2260", ResponseHandler.json(create1));

    create1.get(5, TimeUnit.SECONDS);

    //////////////get loan/////////////////////
    client.get(loanStorageUrl("/"+id1.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get1));

    JsonResponse response2 = get1.get(5, TimeUnit.SECONDS);

    assertThat("MetaData section not populated correctly " + id1.toString(),
      response2.getJson().getJsonObject("metaData").getString("createdByUserId"), is("af23adf0-61ba-4887-bf82-956c4aae2260"));

    ///////////////post loan//////////////////////
    client.post(loanStorageUrl(), j2, StorageTestSuite.TENANT_ID, null,
      ResponseHandler.json(create2));

    create2.get(5, TimeUnit.SECONDS);

    //////////////get loan/////////////////////
    client.get(loanStorageUrl("/"+id2.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get2));

    JsonResponse response4 = get2.get(5, TimeUnit.SECONDS);

    assertThat("MetaData section not populated correctly " + id2.toString(),
      response4.getJson().getJsonObject("metaData"), is(nullValue()));

    ///////////////post loan//////////////////////
    client.post(loanStorageUrl(), j3, StorageTestSuite.TENANT_ID,
      "af23adf0-61ba-4887-bf82-956c4aae2260", ResponseHandler.json(create3));

    create3.get(5, TimeUnit.SECONDS);

    //////////////get loan/////////////////////
    client.get(loanStorageUrl("/"+id3.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get3));

    JsonResponse response6 = get3.get(5, TimeUnit.SECONDS);

    // server should overwrite the field so should not be equal to what was passed in
    assertThat("MetaData section not populated correctly " + id3.toString(),
      response6.getJson().getJsonObject("metaData").getString("createdDate"), not(nowAsISO));
  }

  @Test
  public void canDeleteALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    createLoan(new LoanRequestBuilder().withId(id).create());

    CompletableFuture<TextResponse> deleteCompleted = new CompletableFuture<>();

    client.delete(loanStorageUrl(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.text(deleteCompleted));

    TextResponse deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(loanStorageUrl(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
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

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), requestWithAdditionalProperty,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
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

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), requestWithAdditionalProperty,
      StorageTestSuite.TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
  }

  private JsonResponse getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    URL getInstanceUrl = loanStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private IndividualResource createLoan(JsonObject loanRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
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

  private static URL loanStorageUrl() throws MalformedURLException {
    return loanStorageUrl("");
  }

  private static URL loanStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-storage/loans" + subPath);
  }
}
