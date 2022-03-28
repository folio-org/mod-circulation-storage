package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class TlrFeatureToggleJobAPITest extends ApiTests {

  private static final String TLR_TOGGLE_JOB_URL = "/tlr-toggle-job-storage";
  private static final String TLR_FEATURE_TOGGLE_JOB_TABLE = "tlr_feature_toggle_job";

//  @Before
//  public void beforeEach() throws Exception {
//    CompletableFuture<UpdateResult> future = new CompletableFuture<>();
//    PostgresClient
//      .getInstance(StorageTestSuite.getVertx(), TENANT_ID)
//      .delete(TLR_FEATURE_TOGGLE_JOB_TABLE, new Criterion(), del -> future.complete(del.result()));
//    future.join();
//  }

  @Test
  public void canCreateTlrFeatureToggleJob() throws MalformedURLException, ExecutionException,
    InterruptedException, TimeoutException {

    String jobId = UUID.randomUUID().toString();
    int numberOfUpdates = 10;
    TlrFeatureToggleJob tlrFeatureToggleJob = createTlrFeatureToggleJob(jobId,
      numberOfUpdates);
    JsonResponse postResponse = postTlrFeatureToggleJob(tlrFeatureToggleJob);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response responseById = getTlrFeatureToggleJobById(jobId);
    assertThat(responseById.getStatusCode(), is(HttpURLConnection.HTTP_OK));
  }

  private TlrFeatureToggleJob createTlrFeatureToggleJob(String id, int numberOfUpdates) {
    return new TlrFeatureToggleJob()
      .withId(id)
      .withStatus(TlrFeatureToggleJob.Status.OPEN)
      .withNumberOfUpdatedRequests(numberOfUpdates);
   }

  private JsonResponse postTlrFeatureToggleJob(TlrFeatureToggleJob body)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.post(StorageTestSuite.storageUrl(TLR_TOGGLE_JOB_URL), JsonObject.mapFrom(body),
      StorageTestSuite.TENANT_ID, json(createCompleted));

    return createCompleted.get(5, SECONDS);
  }

  private Response getTlrFeatureToggleJobById(String id) throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(StorageTestSuite.storageUrl(String.format(TLR_TOGGLE_JOB_URL + "/%s", id)),
      StorageTestSuite.TENANT_ID, ResponseHandler.empty(getCompleted));

    return getCompleted.get(5, SECONDS);
  }
}
