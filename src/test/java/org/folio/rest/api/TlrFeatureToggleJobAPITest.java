package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.OPEN;
import static org.folio.rest.support.ResponseHandler.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.clients.RestAssuredClient;
import org.folio.rest.support.spring.TestContextConfiguration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

@ContextConfiguration(classes = TestContextConfiguration.class)
public class TlrFeatureToggleJobAPITest extends ApiTests {
  private static final String TLR_TOGGLE_JOB_URL =
    "/tlr-feature-toggle-job-storage/tlr-feature-toggle-jobs";
  private static final String TLR_FEATURE_TOGGLE_JOB_TABLE = "tlr_feature_toggle_job";

  @ClassRule
  public static final SpringClassRule classRule = new SpringClassRule();
  @Rule
  public final SpringMethodRule methodRule = new SpringMethodRule();

  @Autowired
  public RestAssuredClient restAssuredClient;

  @Before
  public void beforeEach() throws Exception {
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();
    PostgresClient
      .getInstance(StorageTestSuite.getVertx(), TENANT_ID)
      .delete(TLR_FEATURE_TOGGLE_JOB_TABLE, new Criterion(), del -> future.complete(del.result()));
    future.join();
  }

  @Test
  public void canCreateAndDeleteTlrFeatureToggleJob() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    int numberOfUpdates = 10;
    TlrFeatureToggleJob tlrFeatureToggleJob = createTlrFeatureToggleJob(numberOfUpdates);
    JsonResponse postResponse = postTlrFeatureToggleJob(tlrFeatureToggleJob);
    assertThat(postResponse.getStatusCode(), is(HTTP_CREATED));
    checkResponse(numberOfUpdates, postResponse.getJson());

    Response getResponse = getTlrFeatureToggleJobs();
    assertThat(getResponse.getStatusCode(), is(HTTP_OK));
    assertThat(getResponse.getJson().getInteger("totalRecords"), is(1));

    String jobId = postResponse.getJson().getString("id");
    Response deleteResponse = deleteTlrFeatureToggleJob(jobId);
    assertThat(deleteResponse.getStatusCode(), is(HTTP_NO_CONTENT));

    Response getResponseAfterDelete = getTlrFeatureToggleJobs();
    assertThat(getResponseAfterDelete.getStatusCode(), is(HTTP_OK));
    assertThat(getResponseAfterDelete.getJson().getInteger("totalRecords"), is(0));
  }

  @Test
  public void canGetAndUpdateTlrFeatureToggleJobById() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    int numberOfUpdates = 10;
    TlrFeatureToggleJob tlrFeatureToggleJob = createTlrFeatureToggleJob(numberOfUpdates);
    JsonResponse postResponse = postTlrFeatureToggleJob(tlrFeatureToggleJob);
    assertThat(postResponse.getStatusCode(), is(HTTP_CREATED));
    checkResponse(numberOfUpdates, postResponse.getJson());

    String jobId = postResponse.getJson().getString("id");
    Response responseById = getTlrFeatureToggleJobById(jobId);
    assertThat(responseById.getStatusCode(), is(HTTP_OK));
    checkResponse(numberOfUpdates, responseById.getJson());

    TlrFeatureToggleJob tlrFeatureToggleJobForUpdate = createTlrFeatureToggleJob(0)
      .withId(jobId);
    JsonResponse updateResponse = putTlrFeatureToggleJob(tlrFeatureToggleJobForUpdate);
    assertThat(updateResponse.getStatusCode(), is(HTTP_NO_CONTENT));

    Response responseByIdAfterUpdate = getTlrFeatureToggleJobById(jobId);
    checkResponse(0, responseByIdAfterUpdate.getJson());
  }

  @Test
  public void processingShouldSwitchJobStatusFromOpenToInProgress() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException  {

    TlrFeatureToggleJob tlrFeatureToggleJob = createTlrFeatureToggleJob();
    JsonResponse postResponse = postTlrFeatureToggleJob(tlrFeatureToggleJob);
    assertThat(postResponse.getStatusCode(), is(HTTP_CREATED));

    io.restassured.response.Response response = restAssuredClient.post(
      "/tlr-feature-toggle-job-processing", new JsonObject());

    System.out.println(response.getStatusCode());
    System.out.println(response.getBody());
    assertThat(response.getStatusCode(), is(HTTP_NO_CONTENT));
  }

  private void checkResponse(int numberOfUpdates, JsonObject representation) {
    assertThat(representation.getInteger("numberOfUpdatedRequests"), is(numberOfUpdates));
    assertThat(representation.getString("status"), is(OPEN.toString()));
  }

  private TlrFeatureToggleJob createTlrFeatureToggleJob() {
    return new TlrFeatureToggleJob()
      .withStatus(OPEN);
  }

  private TlrFeatureToggleJob createTlrFeatureToggleJob(int numberOfUpdates) {
    return createTlrFeatureToggleJob()
      .withNumberOfUpdatedRequests(numberOfUpdates);
  }

  private JsonResponse postTlrFeatureToggleJob(TlrFeatureToggleJob body)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.post(StorageTestSuite.storageUrl(TLR_TOGGLE_JOB_URL), JsonObject.mapFrom(body),
      TENANT_ID, json(createCompleted));

    return createCompleted.get(5, SECONDS);
  }

  private Response getTlrFeatureToggleJobs() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(StorageTestSuite.storageUrl(TLR_TOGGLE_JOB_URL), TENANT_ID, json(getCompleted));

    return getCompleted.get(5, SECONDS);
  }

  private Response getTlrFeatureToggleJobById(String id) throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(StorageTestSuite.storageUrl(String.format(TLR_TOGGLE_JOB_URL + "/%s", id)),
      TENANT_ID, json(getCompleted));

    return getCompleted.get(5, SECONDS);
  }

  private JsonResponse putTlrFeatureToggleJob(TlrFeatureToggleJob body)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.put(StorageTestSuite.storageUrl(String.format(TLR_TOGGLE_JOB_URL + "/%s",
      body.getId())), JsonObject.mapFrom(body), TENANT_ID, json(createCompleted));

    return createCompleted.get(5, SECONDS);
  }

  private Response deleteTlrFeatureToggleJob(String id) throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> deleteCompleted = new CompletableFuture<>();
    client.delete(StorageTestSuite.storageUrl(String.format(TLR_TOGGLE_JOB_URL + "/%s", id)),
      TENANT_ID, json(deleteCompleted));

    return deleteCompleted.get(5, SECONDS);
  }
}
