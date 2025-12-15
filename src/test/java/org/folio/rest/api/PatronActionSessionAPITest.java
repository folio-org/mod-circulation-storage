package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isOk;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
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
import org.joda.time.format.ISODateTimeFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class PatronActionSessionAPITest extends ApiTests {

  private static final String PATRON_ACTION_SESSION = "patron_action_session";
  private final AssertingRecordClient assertRecordClient = new AssertingRecordClient(client,
    StorageTestSuite.TENANT_ID, InterfaceUrls::patronActionSessionStorageUrl, "patronActionSessions");
  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::loanStorageUrl, "loans");
  private String existingLoanId;

  @BeforeEach
  public void beforeTest() throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();
    PostgresClient
      .getInstance(StorageTestSuite.getVertx(), TENANT_ID)
      .delete(PATRON_ACTION_SESSION, new Criterion(), del -> future.complete(del.result()));
    future.join();

    JsonObject loan = loansClient.create(
      new LoanRequestBuilder().withId(UUID.randomUUID())
        .withItemId(UUID.randomUUID())
        .withUserId(UUID.randomUUID())
        .closed()
        .create()).getJson();

    existingLoanId = loan.getString("id");
  }

  @Test
  public void canGetAllPatronActionSessions() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject firstSession = createPatronActionSession("Check-out");
    JsonObject secondSession = createPatronActionSession("Check-out");

    assertRecordClient.create(firstSession);
    assertRecordClient.create(secondSession);
    MultipleRecords<JsonObject> sessions = assertRecordClient.getAll();

    assertThat(sessions.getTotalRecords(), is(2));
    assertThat(sessions.getRecords().size(), is(2));

    List<String> patronIds = sessions.getRecords().stream()
      .map(json -> json.getString("patronId"))
      .collect(Collectors.toList());

    assertThat(patronIds, containsInAnyOrder(
      firstSession.getString("patronId"),
      secondSession.getString("patronId")));
  }

  @Test
  public void canGetPatronActionSessionsByQueryAndLimit() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject firstSession = createPatronActionSession("Check-out");
    JsonObject secondSession = createPatronActionSession("Check-out");
    JsonObject thirdSession = createPatronActionSession("Check-in");

    assertRecordClient.create(firstSession);
    assertRecordClient.create(secondSession);
    assertRecordClient.create(thirdSession);

    String query = "actionType==Check-out";
    MultipleRecords<JsonObject> sessions = assertRecordClient.getMany(query, null, 1);

    assertThat(sessions.getRecords().size(), is(1));
    assertThat(sessions.getTotalRecords(), is(2));
  }

  @Test
  public void canCreatePatronActionSession() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    String actionType = "Check-out";
    JsonObject request = createPatronActionSession(actionType);

    IndividualResource individualResource = assertRecordClient.create(request);
    assertThat(individualResource.getJson().getString("actionType"), is(actionType));
  }

  @Test
  public void canGetPatronActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject session = createPatronActionSession("Check-out");
    String id = assertRecordClient.create(session).getJson().getString("id");

    IndividualResource individualResource = assertRecordClient.getById(id);

    assertThat(individualResource.getJson().getString("id"), is(id));
    assertThat(individualResource.getJson().getString("patronId"), is(session.getString("patronId")));

  }

  @Test
  public void canDeletePatronActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject request = createPatronActionSession("Check-out");

    String id = assertRecordClient.create(request).getId();
    assertRecordClient.deleteById(UUID.fromString(id));
    MultipleRecords<JsonObject> sessions = assertRecordClient.getAll();

    assertThat(sessions.getRecords().size(), is(0));
    assertThat(sessions.getTotalRecords(), is(0));
  }

  @Test
  public void cannotDeleteNonExistentPatronActionSessionId() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    TextResponse textResponse = assertRecordClient.attemptDeleteById(UUID.randomUUID());

    assertThat(textResponse.getStatusCode(), is(404));
    assertThat(textResponse.getBody(), is("Not found"));
  }

  @Test
  public void canUpdatePatronActiveSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject request = createPatronActionSession("Check-out");

    String id = assertRecordClient.create(request).getJson().getString("id");
    request
      .put("loanId", existingLoanId)
      .put("actionType", "Check-in");
    JsonResponse response = assertRecordClient.attemptPutById(request);

    assertThat("Failed to update patron active session", response.getStatusCode(), is(204));

    IndividualResource updatedPatronActionSession = assertRecordClient.getById(id);

    assertThat(updatedPatronActionSession.getJson().getString("loanId"), is(existingLoanId));
    assertThat(updatedPatronActionSession.getJson().getString("actionType"), is("Check-in"));
  }

  @Test
  public void cannotUpdateNonExistentActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject nonExistPatronActionSession = createPatronActionSession("Check-out");
    JsonResponse response = assertRecordClient.attemptPutById(nonExistPatronActionSession);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  public void canGetPatronActionSessionStorageExpiredSessionPatronIds() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    String firstPatronId = UUID.randomUUID().toString();
    createPatronActionSessionRecords(firstPatronId, "Check-in", DateTime.now().minusDays(3));
    createPatronActionSessionRecords(firstPatronId, "Check-out", DateTime.now().minusDays(3));
    String secondPatronId = UUID.randomUUID().toString();
    createPatronActionSessionRecords(secondPatronId, "Check-in", DateTime.now().minus(1));
    createPatronActionSessionRecords(secondPatronId, "Check-out", DateTime.now().minus(1));

    assertThat(getExpiredPatronSessions("Check-out", 10, DateTime.now()).size(), is(2));

    JsonArray checkInJsonArray = getExpiredPatronSessions("Check-in", 10, DateTime.now().minusDays(2));
    assertThat(checkInJsonArray.size(), is(1));
    assertThat(checkInJsonArray.getJsonObject(0).getString("patronId"), is(firstPatronId));
    assertThat(checkInJsonArray.getJsonObject(0).getString("actionType"), is("Check-in"));

    JsonArray checkOutJsonArray = getExpiredPatronSessions("Check-out", 10, DateTime.now().minusDays(2));
    assertThat(checkOutJsonArray.getJsonObject(0).getString("patronId"), is(firstPatronId));
    assertThat(checkOutJsonArray.getJsonObject(0).getString("actionType"), is("Check-out"));

    assertThat(getExpiredPatronSessions("Check-out", 1, DateTime.now()).size(), is(1));
  }

  @Test
  public void canGetPatronActionSessionStorageExpiredSessionPatronIdsWithoutActionType()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    String firstPatronId = UUID.randomUUID().toString();
    createPatronActionSessionRecords(firstPatronId, "Check-in", DateTime.now().minusDays(2));
    String secondPatronId = UUID.randomUUID().toString();
    createPatronActionSessionRecords(secondPatronId, "Check-out", DateTime.now().minusDays(1));

    JsonArray jsonArray = getExpiredPatronSessions(10, DateTime.now());

    assertThat(jsonArray.size(), is(2));
    assertThat(jsonArray.getJsonObject(0).getString("patronId"), is(firstPatronId));
    assertThat(jsonArray.getJsonObject(0).getString("actionType"), is("Check-in"));
    assertThat(jsonArray.getJsonObject(1).getString("patronId"), is(secondPatronId));
    assertThat(jsonArray.getJsonObject(1).getString("actionType"), is("Check-out"));
  }

  @Test
  public void cannotGetPatronActionSessionStorageExpiredSessionPatronIdsWithWrongActionType()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    String firstPatronId = UUID.randomUUID().toString();
    createPatronActionSessionRecords(firstPatronId, "Check-in", DateTime.now().minusDays(2));

    URL url = StorageTestSuite.storageUrl("/patron-action-session-storage/expired-session-patron-ids",
      "action_type", "WrongType", "session_inactivity_time_limit",
      DateTime.now().minusDays(2).toString(ISODateTimeFormat.dateTime()), "limit", "10");
    JsonResponse response = get(url);
    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Invalid action type value"));
  }

  @Test
  public void cannotGetPatronActionSessionStorageExpiredSessionPatronIds()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    URL url = StorageTestSuite.storageUrl("/patron-action-session-storage/expired-session-patron-ids",
      "action_type", "Check-out", "session_inactivity_time_limit",
      "wrong date", "limit", Integer.toString(10));
    JsonResponse response = get(url);
    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Date cannot be parsed"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Check-in", "Check-out"})
  public void canSaveAndRetrieveSessionWithSessionId(String actionType) throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String sessionId = UUID.randomUUID().toString();
    JsonObject session = createPatronActionSession(actionType)
      .put("sessionId", sessionId);

    assertRecordClient.create(session);
    MultipleRecords<JsonObject> sessions = assertRecordClient.getAll();

    assertThat(sessions.getTotalRecords(), is(1));
    assertThat(sessions.getRecords().size(), is(1));

    JsonObject retrievedSession = sessions.getRecords()
      .stream()
      .findFirst()
      .orElseThrow();

    assertThat(retrievedSession.getString("sessionId"), is(sessionId));
    assertThat(retrievedSession.getString("actionType"), is(actionType));
  }

  private JsonObject createPatronActionSession(String actionType) {
    return new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", existingLoanId)
      .put("actionType", actionType);
  }

  private JsonObject createPatronActionSessionRecords(String patronId, String actionType,
                                                      DateTime createdDate) {

    String userId = UUID.randomUUID().toString();
    JsonObject metaData = new JsonObject()
      .put("createdDate", createdDate.toString(ISODateTimeFormat.dateTime()))
      .put("createdByUserId", userId)
      .put("updatedDate", createdDate.toString(ISODateTimeFormat.dateTime()))
      .put("updatedByUserId", userId);

    JsonObject session = createPatronActionSession(actionType);
    session
      .put("metadata", metaData)
      .put("id", UUID.randomUUID().toString())
      .put("patronId", patronId)
      .put("loanId", existingLoanId);

    CompletableFuture<String> future = new CompletableFuture<>();
    PostgresClient
      .getInstance(StorageTestSuite.getVertx(), TENANT_ID)
      .save(PATRON_ACTION_SESSION, session, update -> future.complete(update.result()));
    future.join();
    return session;
  }

  private JsonArray getExpiredPatronSessions(String actionType, int limit, DateTime lastActionDateLimit)
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    String actionTypeFilter = actionType != null ? actionType : "";
    URL url = StorageTestSuite.storageUrl("/patron-action-session-storage/expired-session-patron-ids",
      "action_type", actionTypeFilter, "session_inactivity_time_limit",
      lastActionDateLimit.toString(ISODateTimeFormat.dateTime()), "limit", Integer.toString(limit));

    JsonResponse response = get(url);
    assertThat(response, isOk());
    return response.getJson().getJsonArray("expiredSessions");
  }

  private JsonArray getExpiredPatronSessions(int limit, DateTime lastActionDateLimit)
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    return getExpiredPatronSessions(null, limit, lastActionDateLimit);
  }

  private JsonResponse get(URL url)
    throws InterruptedException, ExecutionException, TimeoutException {

    final CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    this.client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));
    return getCompleted.get(5, TimeUnit.SECONDS);
  }
}
