package org.folio.rest.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.MetaData;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.*;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
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

public class LoansApiTest {

  private static HttpClient client = new HttpClient(StorageTestSuite.getVertx());

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(loanStorageUrl());
  }

  @After
  public void checkIdsAfterEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

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

    JsonObject loanRequest = loanRequest(id, itemId, userId,
      new DateTime(2017, 6, 27, 10, 23, 43, DateTimeZone.UTC),
      "Open", new DateTime(2017, 7, 27, 10, 23, 43, DateTimeZone.UTC),
      proxyUserId);

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

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

    JsonObject loanRequest = loanRequest(null, itemId, userId,
      new DateTime(2017, 3, 20, 7, 21, 45, DateTimeZone.UTC), "Open",
      new DateTime(2017, 4, 20, 7, 21, 45, DateTimeZone.UTC), proxyUserId);

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

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
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();

    JsonObject loanRequest = loanRequest(id, itemId, userId,
      new DateTime(2017, 2, 27, 21, 14, 43, DateTimeZone.UTC), "Open",
      new DateTime(2017, 3, 29, 21, 14, 43, DateTimeZone.UTC), proxyUserId);

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

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

    //The RAML-Module-Builder converts all date-time formatted strings to UTC
    //and presents the offset as +0000 (which is ISO8601 compatible, but not RFC3339)
    assertThat("due date does not match",
      loan.getString("dueDate"), is("2017-03-29T21:14:43.000+0000"));
  }

  @Test
  public void canCreateALoanWitOnlyRequiredProperties()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    JsonObject loanRequest = new JsonObject();

    loanRequest.put("id", id.toString())
      .put("userId", UUID.randomUUID().toString())
      .put("itemId", UUID.randomUUID().toString())
      .put("action", "checkedout")
      .put("loanDate", new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

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

    UUID id = UUID.randomUUID();

    JsonObject loanRequest = new JsonObject();

    loanRequest.put("id", id.toString())
      .put("userId", UUID.randomUUID().toString())
      .put("itemId", UUID.randomUUID().toString())
      .put("loanDate", "foo")
      .put("returnDate", "bar");

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(422));
  }

  @Test
  public void cannotCreateALoanWithInvalidDates()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject loanRequest = new JsonObject();

    loanRequest.put("id", id.toString())
      .put("userId", UUID.randomUUID().toString())
      .put("itemId", UUID.randomUUID().toString())
      .put("loanDate", "foo")
      .put("action", "checkedout")
      .put("returnDate", "bar");

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

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
  public void canGetALoanById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID proxyUserId = UUID.randomUUID();

    JsonObject loanRequest = loanRequest(id, itemId, userId,
      new DateTime(2016, 10, 15, 8, 26, 53, DateTimeZone.UTC), "Open", null,
      proxyUserId);

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

    assertThat("status is not open",
      loan.getJsonObject("status").getString("name"), is("Open"));
  }

  @Test
  public void cannotGetALoanForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    JsonResponse getResponse = getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canCompleteALoanByReturningTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, 232, DateTimeZone.UTC);

    IndividualResource loan = createLoan(loanRequest(loanDate));

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC)
      .toString(ISODateTimeFormat.dateTime()));

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture();

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
  }

  @Test
  public void canRenewALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    IndividualResource loan = createLoan(loanRequest(loanDate));

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("dueDate", new DateTime(2017, 3, 30, 13, 25, 46, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()))
      .put("action", "renewed")
      .put("renewalCount", 1);

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture();

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

    CompletableFuture<TextResponse> putCompleted = new CompletableFuture();

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

    IndividualResource loan = createLoan(loanRequest(loanDate));

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"));

    CompletableFuture<TextResponse> putCompleted = new CompletableFuture();

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
    ExecutionException,
    UnsupportedEncodingException {

    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());
    createLoan(loanRequest());

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture();

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
    TimeoutException,
    UnsupportedEncodingException {

    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();

    String queryTemplate = loanStorageUrl() + "?query=userId=\"%s\"";

    createLoan(loanRequest().put("userId", firstUserId.toString()));
    createLoan(loanRequest().put("userId", firstUserId.toString()));
    createLoan(loanRequest().put("userId", firstUserId.toString()));
    createLoan(loanRequest().put("userId", firstUserId.toString()));
    createLoan(loanRequest().put("userId", secondUserId.toString()));
    createLoan(loanRequest().put("userId", secondUserId.toString()));
    createLoan(loanRequest().put("userId", secondUserId.toString()));

    CompletableFuture<JsonResponse> firstUserSearchCompleted = new CompletableFuture();
    CompletableFuture<JsonResponse> secondUserSeatchCompleted = new CompletableFuture();

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
    TimeoutException,
    UnsupportedEncodingException {

    UUID userId = UUID.randomUUID();

    String queryTemplate = "query=userId=\"%s\"+and+status.name=\"%s\"";

    createLoan(loanRequest(userId, "Open"));
    createLoan(loanRequest(userId, "Open"));
    createLoan(loanRequest(userId, "Closed"));
    createLoan(loanRequest(userId, "Closed"));
    createLoan(loanRequest(userId, "Closed"));
    createLoan(loanRequest(userId, "Closed"));

    CompletableFuture<JsonResponse> openSearchComppleted = new CompletableFuture();
    CompletableFuture<JsonResponse> closedSearchCompleted = new CompletableFuture();

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
  public void loanHistoryQuery()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    URL url = StorageTestSuite.storageUrl("/loan-storage/loan-history");

    client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse j = getCompleted.get(5, TimeUnit.SECONDS);

    UUID userId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID id = UUID.randomUUID();

    JsonObject j1 = new JsonObject()
    .put("id", id.toString())
    .put("userId", userId.toString())
    .put("itemId", itemId.toString())
    .put("action", "checkedout")
    .put("loanDate", DateTime.parse("2017-03-06T16:04:43.000+02:00",
      ISODateTimeFormat.dateTime()).toString())
    .put("status", new JsonObject().put("name", "Closed"));

    JsonObject j2 = new JsonObject()
    .put("id", id.toString())
    .put("userId", userId.toString())
    .put("itemId", itemId.toString())
    .put("action", "renewal")
    .put("loanDate", DateTime.parse("2017-03-06T16:05:43.000+02:00",
      ISODateTimeFormat.dateTime()).toString())
    .put("status", new JsonObject().put("name", "Opened"));

    JsonObject j3 = new JsonObject()
    .put("id", id.toString())
    .put("userId", userId.toString())
    .put("itemId", itemId.toString())
    .put("action", "checkedin")
    .put("loanDate", DateTime.parse("2017-03-06T16:06:43.000+02:00",
      ISODateTimeFormat.dateTime()).toString())
    .put("status", new JsonObject().put("name", "Closed"));

    CompletableFuture<JsonResponse> create = new CompletableFuture();
    CompletableFuture<JsonResponse> update1 = new CompletableFuture();
    CompletableFuture<JsonResponse> update2 = new CompletableFuture();
    CompletableFuture<TextResponse> delete = new CompletableFuture();

    ///////////////post loan//////////////////////
    client.post(loanStorageUrl(), j1, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(create));

    JsonResponse response1 = create.get(5, TimeUnit.SECONDS);

    //////////////update loan/////////////////////
    client.put(loanStorageUrl("/"+id.toString()), j2, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(update1));

    JsonResponse response2 = update1.get(5, TimeUnit.SECONDS);

    ///////////update again///////////////////////
    client.put(loanStorageUrl("/"+id.toString()), j3, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(update2));

    JsonResponse response3 = update2.get(5, TimeUnit.SECONDS);

    ///////////delete loan//////////////////////////
    client.delete(loanStorageUrl("/"+id.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.text(delete));

    TextResponse response4 = delete.get(5, TimeUnit.SECONDS);

    CompletableFuture<JsonResponse> getCompleted2 = new CompletableFuture();
    CompletableFuture<JsonResponse> getCompleted3 = new CompletableFuture();
    CompletableFuture<JsonResponse> getCompleted4 = new CompletableFuture();

    client.get(url + "?query=id="+id.toString(),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted2));

    JsonResponse finalRes = getCompleted2.get(5, TimeUnit.SECONDS);

    //System.out.println("--->" + finalRes.getJson().encodePrettily());

    client.get(url + "?query="+URLEncoder.encode("userId="+userId.toString(), "UTF8"),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted3));

    JsonResponse finalRes2 = getCompleted3.get(5, TimeUnit.SECONDS);

    //System.out.println("--->" + finalRes2.getJson().encodePrettily());

    client.get(url + "?query=" + URLEncoder.encode("userId="+userId.toString()+" sortBy action", "UTF8"),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted4));

    JsonResponse finalRes4 = getCompleted4.get(5, TimeUnit.SECONDS);

    //System.out.println("--->" + finalRes4.getJson().encodePrettily());

    assertThat("Incorrect number of entries in loan history for id: " + id.toString(),
      finalRes.getJson().getJsonArray("loans").size(), is(4));

    assertThat("Incorrect value of first loan in res set - should be deleted " + id.toString(),
      finalRes.getJson().getJsonArray("loans").getJsonObject(0).getString("action"), is("deleted"));

    assertThat("Incorrect number of entries in loan history for userId: " + userId.toString(),
      finalRes2.getJson().getJsonArray("loans").size(), is(4));

    assertThat("Incorrect value oof first loan in res set - should be checkedin " + id.toString(),
      finalRes4.getJson().getJsonArray("loans").getJsonObject(0).getString("action"), is("checkedin"));
  }

  @Test
  public void metaDataPopulated()
    throws Exception {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    URL url = StorageTestSuite.storageUrl("/loan-storage/loan-history");

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

    CompletableFuture<JsonResponse> create1 = new CompletableFuture();
    CompletableFuture<JsonResponse> get1    = new CompletableFuture();
    CompletableFuture<JsonResponse> create2 = new CompletableFuture();
    CompletableFuture<JsonResponse> get2    = new CompletableFuture();
    CompletableFuture<JsonResponse> create3 = new CompletableFuture();
    CompletableFuture<JsonResponse> get3    = new CompletableFuture();

    ///////////////post loan//////////////////////
    client.post(loanStorageUrl(), j1, StorageTestSuite.TENANT_ID,
      "af23adf0-61ba-4887-bf82-956c4aae2260", ResponseHandler.json(create1));

    JsonResponse response1 = create1.get(5, TimeUnit.SECONDS);

    //////////////get loan/////////////////////
    client.get(loanStorageUrl("/"+id1.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get1));

    JsonResponse response2 = get1.get(5, TimeUnit.SECONDS);

    assertThat("MetaData section not populated correctly " + id1.toString(),
      response2.getJson().getJsonObject("metaData").getString("createdByUserId"), is("af23adf0-61ba-4887-bf82-956c4aae2260"));

    ///////////////post loan//////////////////////
    client.post(loanStorageUrl(), j2, StorageTestSuite.TENANT_ID, null,
      ResponseHandler.json(create2));

    JsonResponse response3 = create2.get(5, TimeUnit.SECONDS);

    //////////////get loan/////////////////////
    client.get(loanStorageUrl("/"+id2.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get2));

    JsonResponse response4 = get2.get(5, TimeUnit.SECONDS);

    assertThat("MetaData section not populated correctly " + id2.toString(),
      response4.getJson().getJsonObject("metaData"), is(nullValue()));

    ///////////////post loan//////////////////////
    client.post(loanStorageUrl(), j3, StorageTestSuite.TENANT_ID,
      "af23adf0-61ba-4887-bf82-956c4aae2260", ResponseHandler.json(create3));

    JsonResponse response5 = create3.get(5, TimeUnit.SECONDS);

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
    ExecutionException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();

    createLoan(loanRequest(id, UUID.randomUUID(), UUID.randomUUID(),
      DateTime.now(), "Open", null, null));

    CompletableFuture<TextResponse> deleteCompleted = new CompletableFuture();

    client.delete(loanStorageUrl(String.format("/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.text(deleteCompleted));

    TextResponse deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture();

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

    JsonObject requestWithAdditionalProperty = loanRequest(id,
      UUID.randomUUID(), UUID.randomUUID(), DateTime.now(), "Open", null, null);

    requestWithAdditionalProperty.put("somethingAdditional", "foo");

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture();

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

    JsonObject requestWithAdditionalProperty = loanRequest(id,
      UUID.randomUUID(), UUID.randomUUID(), DateTime.now(), "Open", null, null);

    requestWithAdditionalProperty.getJsonObject("status")
      .put("somethingAdditional", "foo");

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture();

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
    TimeoutException,
    UnsupportedEncodingException {

    URL getInstanceUrl = loanStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private IndividualResource createLoan(JsonObject loanRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  private JsonObject loanRequest() {
    return loanRequest(UUID.randomUUID(), UUID.randomUUID(),
      UUID.randomUUID(), DateTime.parse("2017-03-06T16:04:43.000+02:00",
        ISODateTimeFormat.dateTime()), "Open", null, null);
  }

  private JsonObject loanRequest(UUID userId, String statusName) {
    Random random = new Random();

    return loanRequest(UUID.randomUUID(), UUID.randomUUID(),
      userId, DateTime.now().minusDays(random.nextInt(10)), statusName, null,
      null);
  }

  private JsonObject loanRequest(DateTime loanDate) {
    return loanRequest(UUID.randomUUID(), UUID.randomUUID(),
      UUID.randomUUID(), loanDate, "Open", loanDate.plus(Period.days(14)), null);
  }

  private JsonObject loanRequest(
    UUID id,
    UUID itemId,
    UUID userId,
    DateTime loanDate,
    String statusName,
    DateTime dueDate, UUID proxyUserId) {

    JsonObject loanRequest = new JsonObject();

    if(id != null) {
      loanRequest.put("id", id.toString());
    }

    loanRequest
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("action", "checkedout")
      .put("loanDate", loanDate.toString(ISODateTimeFormat.dateTime()))
      .put("status", new JsonObject().put("name", statusName));

    if(proxyUserId != null) {
      loanRequest.put("proxyUserId", proxyUserId.toString());
    }

    if(statusName == "Closed") {
      loanRequest.put("returnDate",
        loanDate.plusDays(1).plusHours(4).toString(ISODateTimeFormat.dateTime()));
    }

    if(dueDate != null) {
      loanRequest.put("dueDate", dueDate.toString(ISODateTimeFormat.dateTime()));
    }

    return loanRequest;
  }

  private static URL loanStorageUrl() throws MalformedURLException {
    return loanStorageUrl("");
  }

  private static URL loanStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-storage/loans" + subPath);
  }
}
