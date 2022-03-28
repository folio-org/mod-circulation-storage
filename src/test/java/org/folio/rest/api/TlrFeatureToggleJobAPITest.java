package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.OPEN;
import static org.folio.rest.support.ResponseHandler.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class TlrFeatureToggleJobAPITest extends ApiTests {

  private static final String TLR_TOGGLE_JOB_URL = "/tlr-toggle-job-storage";
  private static final String TLR_FEATURE_TOGGLE_JOB_TABLE = "tlr_feature_toggle_job";

  @Before
  public void beforeEach() throws Exception {
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();
    PostgresClient
      .getInstance(StorageTestSuite.getVertx(), TENANT_ID)
      .delete(TLR_FEATURE_TOGGLE_JOB_TABLE, new Criterion(), del -> future.complete(del.result()));
    future.join();
  }

  @Test
  public void canCreateTlrFeatureToggleJob() throws MalformedURLException, ExecutionException,
    InterruptedException, TimeoutException {

    String jobId = UUID.randomUUID().toString();
    int numberOfUpdates = 10;
    TlrFeatureToggleJob tlrFeatureToggleJob = createTlrFeatureToggleJob(jobId, numberOfUpdates);
    JsonResponse postResponse = postTlrFeatureToggleJob(tlrFeatureToggleJob);
    assertThat(postResponse.getStatusCode(), is(HTTP_CREATED));
    checkResponse(jobId, numberOfUpdates, postResponse.getJson());

    Response responseById = getTlrFeatureToggleJobById(jobId);
    assertThat(responseById.getStatusCode(), is(HTTP_OK));
    checkResponse(jobId, numberOfUpdates, responseById.getJson());
  }

  private void checkResponse(String jobId, int numberOfUpdates, JsonObject representation) {
    assertThat(representation.getString("id"), is(jobId));
    assertThat(representation.getInteger("numberOfUpdatedRequests"), is(numberOfUpdates));
    assertThat(representation.getString("status"), is(OPEN.toString()));
  }

  private TlrFeatureToggleJob createTlrFeatureToggleJob(String id, int numberOfUpdates) {
    return new TlrFeatureToggleJob()
      .withId(id)
      .withStatus(OPEN)
      .withNumberOfUpdatedRequests(numberOfUpdates);
   }

  private JsonResponse postTlrFeatureToggleJob(TlrFeatureToggleJob body)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.post(StorageTestSuite.storageUrl(TLR_TOGGLE_JOB_URL), JsonObject.mapFrom(body),
      TENANT_ID, json(createCompleted));

    return createCompleted.get(5, SECONDS);
  }

  private Response getTlrFeatureToggleJobById(String id) throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(StorageTestSuite.storageUrl(String.format(TLR_TOGGLE_JOB_URL + "/%s", id)),
      TENANT_ID, json(getCompleted));

    return getCompleted.get(5, SECONDS);
  }
}
