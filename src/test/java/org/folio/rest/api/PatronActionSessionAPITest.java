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
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;

public class PatronActionSessionAPITest extends ApiTests {

  private static final String PATRON_ACTION_SESSION = "patron_action_session";
  private final AssertingRecordClient assertRecordClient = new AssertingRecordClient(client,
    StorageTestSuite.TENANT_ID, InterfaceUrls::patronActionSessionStorageUrl, "patronActionSessions");

  @Before
  public void cleanUp() {
    CompletableFuture<UpdateResult> future = new CompletableFuture<>();
    PostgresClient
      .getInstance(StorageTestSuite.getVertx(), TENANT_ID)
      .delete(PATRON_ACTION_SESSION, new Criterion(), del -> future.complete(del.result()));
    future.join();
  }

  @Test
  public void canGetAllPatronActionSessions() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject firstSession = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");

    JsonObject secondSession = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");

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

    JsonObject firstSession = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");

    JsonObject secondSession = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");

    JsonObject thirdSession = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-in");

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

    JsonObject request = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");
    assertRecordClient.create(request);
  }

  @Test
  public void canGetPatronActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject session = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");
    String id = assertRecordClient.create(session).getJson().getString("id");

    IndividualResource individualResource = assertRecordClient.getById(id);

    assertThat(individualResource.getJson().getString("id"), is(id));
    assertThat(individualResource.getJson().getString("patronId"), is(session.getString("patronId")));

  }

  @Test
  public void canDeletePatronActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    UUID id = UUID.randomUUID();
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");

    assertRecordClient.create(request).getId();
    assertRecordClient.deleteById(id);
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

    JsonObject request = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");

    String id = assertRecordClient.create(request).getJson().getString("id");
    String newLoanId = UUID.randomUUID().toString();
    request
      .put("loanId", newLoanId)
      .put("actionType", "Check-in");
    JsonResponse response = assertRecordClient.attemptPutById(request);

    assertThat("Failed to update patron active session", response.getStatusCode(), is(204));

    IndividualResource updatedPatronActionSession = assertRecordClient.getById(id);

    assertThat(updatedPatronActionSession.getJson().getString("loanId"), is(newLoanId));
    assertThat(updatedPatronActionSession.getJson().getString("actionType"), is("Check-in"));
  }

  @Test
  public void cannotUpdateNonExistentActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject nonExistPatronActionSession = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");
    JsonResponse response = assertRecordClient.attemptPutById(nonExistPatronActionSession);

    assertThat(response.getStatusCode(), is(404));
  }
}
