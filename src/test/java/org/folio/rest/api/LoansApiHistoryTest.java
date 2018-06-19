package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.*;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoansApiHistoryTest extends ApiTests {
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
  public void creatingALoanCreatesHistoryEntry()
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
      .withLoanDate(new DateTime(2017, 6, 27, 10, 23, 43, DateTimeZone.UTC))
      .withStatus("Open")
      .withAction("checkedout")
      .withItemStatus("Checked out")
      .withDueDate(new DateTime(2017, 7, 27, 10, 23, 43, DateTimeZone.UTC))
      .create();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(loanStorageUrl(), loanRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse createResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", createResponse.getBody()),
      createResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(loanStorageHistoryUrl("?query=id="+id.toString()),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse historyResponse = getCompleted.get(5, TimeUnit.SECONDS);

    List<JsonObject> entries = JsonArrayHelper.toList(
      historyResponse.getJson().getJsonArray("loans"));

    assertThat("Incorrect number of entries in loan history for id: " + id.toString(),
      entries.size(), is(1));

    JsonObject loan = entries.get(0);

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

    //The RAML-Module-Builder converts all date-time formatted strings to UTC
    //and presents the offset as +0000 (which is ISO8601 compatible, but not RFC3339)
    assertThat("due date does not match",
      loan.getString("dueDate"), is("2017-07-27T10:23:43.000+0000"));
  }

  @Test
  public void replacingALoanCreatesHistoryRecord()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID id = UUID.randomUUID();

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture();

    client.post(loanStorageUrl(), new LoanRequestBuilder().withId(id).create(),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(createCompleted));

    JsonResponse createResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan: %s", createResponse.getBody()),
      createResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject updatedLoanRequest = createResponse.getJson().copy();

    updatedLoanRequest
      .put("dueDate", new DateTime(2017, 3, 30, 13, 25, 46, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()))
      .put("action", "renewed")
      .put("itemStatus", "Checked out")
      .put("renewalCount", 1);

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture();

    client.put(loanStorageUrl(String.format("/%s", id)), updatedLoanRequest,
      StorageTestSuite.TENANT_ID, ResponseHandler.json(putCompleted));

    JsonResponse putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan: %s", putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture();

    client.get(loanStorageHistoryUrl("?query=id="+id.toString()),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse historyResponse = getCompleted.get(5, TimeUnit.SECONDS);

    List<JsonObject> entries = JsonArrayHelper.toList(
      historyResponse.getJson().getJsonArray("loans"));

    assertThat("Incorrect number of entries in loan history for id: " + id.toString(),
      entries.size(), is(2));

    JsonObject entry = entries.get(0);

    //The RAML-Module-Builder converts all date-time formatted strings to UTC
    //and presents the offset as +0000 (which is ISO8601 compatible, but not RFC3339)
    assertThat(entry.getString("dueDate"),
      is("2017-03-30T13:25:46.000+0000"));

    assertThat("status is not open",
      entry.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action is not renewed",
      entry.getString("action"), is("renewed"));

    assertThat("renewal count is not 1",
      entry.getInteger("renewalCount"), is(1));

    assertThat("item status is not checked out",
      entry.getString("itemStatus"), is("Checked out"));
  }

  @Test
  public void loanHistoryQuery()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    URL url = loanStorageHistoryUrl();

    client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse j = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(j.getStatusCode(), is(HTTP_OK));

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

    CompletableFuture<JsonResponse> create = new CompletableFuture<>();
    CompletableFuture<JsonResponse> update1 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> update2 = new CompletableFuture<>();
    CompletableFuture<TextResponse> delete = new CompletableFuture<>();
    CompletableFuture<JsonResponse> getDefaultLimit = new CompletableFuture<>();

    client.get(loanStorageHistoryUrl(), StorageTestSuite.TENANT_ID, 
    		ResponseHandler.json(getDefaultLimit));
    
    ///////////////post loan//////////////////////
    client.post(loanStorageUrl(), j1, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(create));

    JsonResponse response1 = create.get(5, TimeUnit.SECONDS);

    assertThat(response1.getStatusCode(), is(HTTP_CREATED));

    //////////////update loan/////////////////////
    client.put(loanStorageUrl("/"+id.toString()), j2, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(update1));

    JsonResponse response2 = update1.get(5, TimeUnit.SECONDS);

    assertThat(response2.getStatusCode(), is(HTTP_NO_CONTENT));

    ///////////update again///////////////////////
    client.put(loanStorageUrl("/"+id.toString()), j3, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(update2));

    JsonResponse response3 = update2.get(5, TimeUnit.SECONDS);

    assertThat(response3.getStatusCode(), is(HTTP_NO_CONTENT));

    ///////////delete loan//////////////////////////
    client.delete(loanStorageUrl("/"+id.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.text(delete));

    TextResponse response4 = delete.get(5, TimeUnit.SECONDS);

    assertThat(response4.getStatusCode(), is(HTTP_NO_CONTENT));

    CompletableFuture<JsonResponse> getCompleted2 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> getCompleted3 = new CompletableFuture<>();
    CompletableFuture<JsonResponse> getCompleted4 = new CompletableFuture<>();

    client.get(url + "?query=id="+id.toString(),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted2));

    JsonResponse finalRes = getCompleted2.get(5, TimeUnit.SECONDS);

    assertThat(finalRes.getStatusCode(), is(HTTP_OK));

    client.get(url + "?query="+ URLEncoder.encode("userId="+userId.toString(), "UTF8"),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted3));

    JsonResponse finalRes2 = getCompleted3.get(5, TimeUnit.SECONDS);

    assertThat(finalRes2.getStatusCode(), is(HTTP_OK));

    client.get(url + "?query=" + URLEncoder.encode("userId="+userId.toString()+" sortBy action", "UTF8"),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted4));

    JsonResponse finalRes4 = getCompleted4.get(5, TimeUnit.SECONDS);

    assertThat(finalRes4.getStatusCode(), is(HTTP_OK));

    assertThat("Should have array property",
      finalRes.getJson().containsKey("loans"), is(true));

    assertThat("Incorrect number of entries in loan history for id: " + id.toString(),
      finalRes.getJson().getJsonArray("loans").size(), is(4));

    //Trigger used to have a special case for delete, it looks like standard audit implementation does not have this
    assertThat("Incorrect value of first loan in res set - should be deleted " + id.toString(),
      finalRes.getJson().getJsonArray("loans").getJsonObject(0).getString("action"), is("deleted"));

    assertThat("Incorrect number of entries in loan history for userId: " + userId.toString(),
      finalRes2.getJson().getJsonArray("loans").size(), is(4));

    assertThat("Incorrect value oof first loan in res set - should be checkedin " + id.toString(),
      finalRes4.getJson().getJsonArray("loans").getJsonObject(0).getString("action"), is("checkedin"));
  }

  private static URL loanStorageUrl() throws MalformedURLException {
    return loanStorageUrl("");
  }

  private static URL loanStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-storage/loans" + subPath);
  }

  private static URL loanStorageHistoryUrl() throws MalformedURLException {
    return loanStorageHistoryUrl("");
  }

  private static URL loanStorageHistoryUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-storage/loan-history" + subPath);
  }
}
