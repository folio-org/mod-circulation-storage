package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;

import org.junit.Before;
import org.junit.Test;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.MultipleRecords;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;

public class PatronActionSessionAPITest extends ApiTests {

  private static final String PATRON_ACTION_SESSION = "patron_action_session";
  private final AssertingRecordClient assertRecordClient = new AssertingRecordClient(client,
    StorageTestSuite.TENANT_ID, InterfaceUrls::patronActionSessionStorageUrl, "patronActionSessions");
  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::loanStorageUrl, "loans");
  private String existingLoanId;

  @Before
  public void beforeTest() throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    CompletableFuture<UpdateResult> future = new CompletableFuture<>();
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

    JsonObject firstSession = createSession("Check-out");
    JsonObject secondSession = createSession("Check-out");

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

    JsonObject firstSession = createSession("Check-out");
    JsonObject secondSession = createSession("Check-out");
    JsonObject thirdSession = createSession("Check-in");

    assertRecordClient.create(firstSession);
    assertRecordClient.create(secondSession);
    assertRecordClient.create(thirdSession);

    String query = "query=actionType==Check-out&limit=1";
    MultipleRecords<JsonObject> sessions = assertRecordClient.getMany(query);

    assertThat(sessions.getRecords().size(), is(1));
    assertThat(sessions.getTotalRecords(), is(2));
  }

  @Test
  public void canCreatePatronActionSession() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    String actionType = "Check-out";
    JsonObject request = createSession(actionType);

    IndividualResource individualResource = assertRecordClient.create(request);
    assertThat(individualResource.getJson().getString("actionType"), is(actionType));
  }

  @Test
  public void canGetPatronActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject session = createSession("Check-out");
    String id = assertRecordClient.create(session).getJson().getString("id");

    IndividualResource individualResource = assertRecordClient.getById(id);

    assertThat(individualResource.getJson().getString("id"), is(id));
    assertThat(individualResource.getJson().getString("patronId"), is(session.getString("patronId")));

  }

  @Test
  public void canDeletePatronActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject request = createSession("Check-out");

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

    JsonObject request = createSession("Check-out");

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

    JsonObject nonExistPatronActionSession = createSession("Check-out");
    JsonResponse response = assertRecordClient.attemptPutById(nonExistPatronActionSession);

    assertThat(response.getStatusCode(), is(404));
  }

  private JsonObject createSession(String actionType){
    JsonObject jsonObject = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", existingLoanId)
      .put("actionType", actionType);
    return jsonObject;
  }
}
