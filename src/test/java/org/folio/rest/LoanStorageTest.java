package org.folio.rest;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.support.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoanStorageTest {

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

    JsonObject loanRequest = loanRequest(id, itemId, userId,
      LocalDate.of(2017, 02, 27));

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

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));
  }

  @Test
  public void canCreateALoanWithoutAnId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID itemId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    JsonObject loanRequest = loanRequest(null, itemId, userId,
      LocalDate.of(2017, 03, 20));

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

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));
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

    JsonObject loanRequest = loanRequest(id, itemId, userId,
      LocalDate.of(2017, 02, 27));

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

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));
  }

  @Test
  public void cannotCreateALoanWithInvalidLoanDate()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject loanRequest = new JsonObject();

      loanRequest.put("id", id.toString())
      .put("userId", UUID.randomUUID().toString())
      .put("itemId", UUID.randomUUID().toString())
      .put("loanDate", "2017");

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(), containsString("loanDate  must match"));
  }

  @Test
  public void cannotCreateALoanWithoutRequiredFields()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject loanRequest = new JsonObject();

    loanRequest.put("id", id.toString());

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Creating the loan should fail: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    assertThat(response.getBody(), containsString("loanDate  may not be null"));
    assertThat(response.getBody(), containsString("itemId  may not be null"));
    assertThat(response.getBody(), containsString("userId  may not be null"));
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

    JsonObject loanRequest = loanRequest(id, itemId, userId, LocalDate.now());

    createLoan(loanRequest);

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get loan: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject loan = getResponse.getJson();

    assertThat("id does not match",
      loan.getString("id"), is(id.toString()));

    assertThat("user id does not match",
      loan.getString("userId"), is(userId.toString()));

    assertThat("item id does not match",
      loan.getString("itemId"), is(itemId.toString()));
  }

  @Test
  public void loanNotFoundForUnknownId()
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

    LocalDate loanDate = LocalDate.of(2017, 03, 01);

    IndividualResource loan = createLoan(loanRequest(loanDate));

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan.put("returnDate", LocalDate.of(2017, 03, 05).toString());

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture();

    client.put(loanStorageUrl(String.format("/%s", loan.getId())), returnedLoan,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(putCompleted));

    JsonResponse putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    JsonResponse updatedLoan = getById(UUID.fromString(loan.getId()));

    assertThat(updatedLoan.getJson().getString("returnDate"), is("2017-03-05"));
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

  private JsonResponse getById(UUID id)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    URL getInstanceUrl = loanStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  @Test
  public void canDeleteALoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    createLoan(loanRequest(id, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now()));

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
      UUID.randomUUID(), LocalDate.now());
  }

  private JsonObject loanRequest(LocalDate loanDate) {
    return loanRequest(UUID.randomUUID(), UUID.randomUUID(),
      UUID.randomUUID(), loanDate);
  }

  private JsonObject loanRequest(
    UUID id,
    UUID itemId,
    UUID userId,
    LocalDate loanDate) {

    JsonObject loanRequest = new JsonObject();

    if(id != null) {
      loanRequest.put("id", id.toString());
    }

    return loanRequest
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("loanDate", loanDate.toString());
  }

  private static URL loanStorageUrl() throws MalformedURLException {
    return loanStorageUrl("");
  }

  private static URL loanStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-storage/loans" + subPath);
  }
}
