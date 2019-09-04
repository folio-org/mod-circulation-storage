package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsArrayContainingInAnyOrder.arrayContainingInAnyOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;

import org.junit.Before;
import org.junit.Test;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;

public class PatronActionSessionAPITest extends ApiTests {

  private static final String PATRON_ACTION_SESSION = "patron_action_session";
  private static final String PATRON_ACTION_SESSION_URL = "/patron-action-session-storage/patron-action-sessions";

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

    postPatronActionSession(firstSession);
    postPatronActionSession(secondSession);

    CompletableFuture<JsonResponse> getAllCompleted = new CompletableFuture<>();

    client.get(StorageTestSuite.storageUrl(PATRON_ACTION_SESSION_URL), TENANT_ID,
      ResponseHandler.json(getAllCompleted));
    JsonResponse response = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Failed to get all patron notice policies", response.getStatusCode(), is(200));

    JsonArray sessions = response.getJson().getJsonArray("patronActionSessions");

    assertThat(sessions.size(), is(2));
    assertThat(response.getJson().getInteger("totalRecords"), is(2));

    String[] patronIdArray = sessions.stream()
      .map(JsonObject.class :: cast)
      .map(json -> json.getString("patronId"))
      .toArray(String[] :: new);

    assertThat(patronIdArray,
      arrayContainingInAnyOrder(firstSession.getString("patronId"), secondSession.getString("patronId")));
  }

  @Test
  public void canCreatePatronActionSession() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    JsonObject request = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");
    JsonResponse response = postPatronActionSession(request);
    assertThat("Failed to create patron active session", response.getStatusCode(), is(201));
  }

  @Test
  public void canGetPatronActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject session = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");
    String id = postPatronActionSession(session).getJson().getString("id");

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(StorageTestSuite.storageUrl(PATRON_ACTION_SESSION_URL + "/" + id),
      TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("id"), is(id));
    assertThat(response.getJson().getString("patronId"), is(session.getString("patronId")));

  }

  @Test
  public void canDeletePatronActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject request = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");
    String id = postPatronActionSession(request).getJson().getString("id");
    JsonResponse response = deletePatronActionSession(id);

    assertThat(response.getStatusCode(), is(204));
  }

  @Test
  public void cannotDeleteNonExistPatronActionSessionId() throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    String id = UUID.randomUUID().toString();
    JsonResponse response = deletePatronActionSession(id);

    assertThat(response.getStatusCode(), is(404));
    assertThat(response.getBody(), is("Not found"));
  }

  @Test
  public void canUpdatePatronActiveSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject request = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");

    String id = postPatronActionSession(request).getJson().getString("id");
    String newLoanId = UUID.randomUUID().toString();
    request
      .put("loanId", newLoanId)
      .put("actionType", "Check-in");
    JsonResponse response = putPatronActionSession(request);

    assertThat("Failed to update patron active session", response.getStatusCode(), is(204));

    JsonObject updatedPolicy = getPatronActionSessionById(id).getJson();
    assertThat(updatedPolicy.getString("loanId"), is(newLoanId));
    assertThat(updatedPolicy.getString("actionType"), is ("Check-in"));
  }

  @Test
  public void cannotUpdateNonExistActionSession() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject nonExistPatronActionSession = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("patronId", UUID.randomUUID().toString())
      .put("loanId", UUID.randomUUID().toString())
      .put("actionType", "Check-out");
    JsonResponse response = putPatronActionSession(nonExistPatronActionSession);

    assertThat(response.getStatusCode(), is(404));
  }


  private JsonResponse postPatronActionSession(JsonObject entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.post(StorageTestSuite.storageUrl(PATRON_ACTION_SESSION_URL),
      entity, TENANT_ID, ResponseHandler.json(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse putPatronActionSession(JsonObject entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();
    client.put(StorageTestSuite.storageUrl(PATRON_ACTION_SESSION_URL + "/" + entity.getString("id")),
      entity, TENANT_ID, ResponseHandler.json(updateCompleted));

    return updateCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse getPatronActionSessionById(String id)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(StorageTestSuite.storageUrl(PATRON_ACTION_SESSION_URL + "/" + id),
      TENANT_ID, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse deletePatronActionSession(String id) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> deleteCompleted = new CompletableFuture<>();
    client.delete(StorageTestSuite.storageUrl(PATRON_ACTION_SESSION_URL + "/" + id),
      TENANT_ID, ResponseHandler.json(deleteCompleted));

    return deleteCompleted.get(5, TimeUnit.SECONDS);
  }
}
