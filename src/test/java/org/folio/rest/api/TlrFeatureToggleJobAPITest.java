package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.vertx.core.json.JsonObject.mapFrom;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.DONE;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.TlrFeatureToggleJob.Status.OPEN;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_AWAITING_DELIVERY;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_AWAITING_PICKUP;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_IN_TRANSIT;
import static org.folio.rest.support.builders.RequestRequestBuilder.OPEN_NOT_YET_FILLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.rest.configuration.TlrSettingsConfiguration;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.jaxrs.model.TlrFeatureToggleJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.folio.rest.support.clients.RestAssuredClient;
import org.folio.rest.support.spring.TestContextConfiguration;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

@ContextConfiguration(classes = TestContextConfiguration.class)
public class TlrFeatureToggleJobAPITest extends ApiTests {
  private static final String TLR_TOGGLE_JOB_URL =
    "/tlr-feature-toggle-job-storage/tlr-feature-toggle-jobs";
  private static final String TLR_TOGGLE_JOB_START_URL = "/tlr-feature-toggle-job/start";
  private static final String TLR_FEATURE_TOGGLE_JOB_TABLE = "tlr_feature_toggle_job";
  private static final String REQUEST_STORAGE_URL = "/request-storage/requests";

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
    StorageTestSuite.deleteAll(StorageTestSuite.storageUrl(REQUEST_STORAGE_URL));
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
      TLR_TOGGLE_JOB_START_URL, new JsonObject());

    assertThat(response.getStatusCode(), is(202));

    Response updatedJob = getTlrFeatureToggleJobById(
      postResponse.getJson().getString("id"));
    assertThat(updatedJob.getJson().getString("status"), is(IN_PROGRESS.toString()));
  }

  @Test
  public void processingShouldRespondWith202WhenThereAreRunningJobs() throws MalformedURLException,
    ExecutionException, InterruptedException, TimeoutException  {

    TlrFeatureToggleJob tlrFeatureToggleJob = createTlrFeatureToggleJobWithStatus(IN_PROGRESS);
    JsonResponse postResponse = postTlrFeatureToggleJob(tlrFeatureToggleJob);
    assertThat(postResponse.getStatusCode(), is(HTTP_CREATED));

    io.restassured.response.Response response = restAssuredClient.post(
      TLR_TOGGLE_JOB_START_URL, new JsonObject());
    assertThat(response.getStatusCode(), is(202));
  }

  @Test
  public void processingShouldRecalculatePositionsForTitleLevelRequests()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    stubTlrSettings(true);
    UUID firstInstanceId = UUID.randomUUID();
    UUID secondInstanceId = UUID.randomUUID();
    var initialQueue = createItemLevelRequestsQueue(firstInstanceId, secondInstanceId);

    TlrFeatureToggleJob tlrFeatureToggleJob = createTlrFeatureToggleJob();
    JsonResponse postResponse = postTlrFeatureToggleJob(tlrFeatureToggleJob);
    assertThat(postResponse.getStatusCode(), is(HTTP_CREATED));
    restAssuredClient.post(TLR_TOGGLE_JOB_START_URL, new JsonObject());
    String jobId = postResponse.getJson().getString("id");
    await().until(() -> getTlrFeatureToggleJobById(jobId)
      .getJson().getString("status"), is(DONE.toString()));
    assertThat(getTlrFeatureToggleJobById(jobId).getJson().getInteger(
      "numberOfUpdatedRequests"), is(7));

    Response getResponse = getRequests();
    assertThat(getResponse.getStatusCode(), is(200));
    List<JsonObject> updatedRequests = JsonArrayHelper.toList(getResponse.getJson()
      .getJsonArray("requests"));
    assertThat(updatedRequests, hasSize(7));
    var firstInstanceRequestsQueue = updatedRequests.stream()
      .filter(request -> firstInstanceId.toString().equals(request.getString("instanceId")))
      .collect(Collectors.toList());
    checkPosition(initialQueue.get(0), firstInstanceRequestsQueue, 1);
    checkPosition(initialQueue.get(3), firstInstanceRequestsQueue, 2);
    checkPosition(initialQueue.get(1), firstInstanceRequestsQueue, 3);
    checkPosition(initialQueue.get(4), firstInstanceRequestsQueue, 4);
    checkPosition(initialQueue.get(2), firstInstanceRequestsQueue, 5);

    var secondInstanceRequestsQueue = updatedRequests.stream()
      .filter(request -> secondInstanceId.toString().equals(request.getString("instanceId")))
      .collect(Collectors.toList());
    checkPosition(initialQueue.get(5), secondInstanceRequestsQueue, 1);
    checkPosition(initialQueue.get(6), secondInstanceRequestsQueue, 2);
  }

  @Test
  public void processingShouldRecalculatePositionsForItemLevelRequests()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    stubTlrSettings(false);
    UUID firstItemId = UUID.randomUUID();
    UUID secondItemId = UUID.randomUUID();
    var initialQueue = createTitleLevelRequestsQueue(firstItemId, secondItemId);

    TlrFeatureToggleJob tlrFeatureToggleJob = createTlrFeatureToggleJob();
    JsonResponse postResponse = postTlrFeatureToggleJob(tlrFeatureToggleJob);
    assertThat(postResponse.getStatusCode(), is(HTTP_CREATED));
    restAssuredClient.post(TLR_TOGGLE_JOB_START_URL, new JsonObject());
    String jobId = postResponse.getJson().getString("id");
    await().until(() -> getTlrFeatureToggleJobById(jobId)
      .getJson().getString("status"), is(DONE.toString()));
    assertThat(getTlrFeatureToggleJobById(jobId).getJson().getInteger(
      "numberOfUpdatedRequests"), is(4));

    Response getResponse = getRequests();
    assertThat(getResponse.getStatusCode(), is(200));
    List<JsonObject> updatedRequests = JsonArrayHelper.toList(getResponse.getJson()
      .getJsonArray("requests"));
    assertThat(updatedRequests, hasSize(4));
    var firstItemRequestsQueue = updatedRequests.stream()
      .filter(request -> firstItemId.toString().equals(request.getString("itemId")))
      .collect(Collectors.toList());
    checkPosition(initialQueue.get(0), firstItemRequestsQueue, 1);
    checkPosition(initialQueue.get(1), firstItemRequestsQueue, 2);

    var secondItemRequestsQueue = updatedRequests.stream()
      .filter(request -> secondItemId.toString().equals(request.getString("itemId")))
      .collect(Collectors.toList());
    checkPosition(initialQueue.get(2), secondItemRequestsQueue, 1);
    checkPosition(initialQueue.get(3), secondItemRequestsQueue, 2);
  }

  private void checkPosition(JsonObject jsonObject, List<JsonObject> queue,
    int expectedPosition) {

    assertThat(queue.stream()
      .filter(json -> json.getString("id").equals(jsonObject.getString("id")))
      .findFirst()
      .map(json -> json.getInteger("position"))
      .get(), is(expectedPosition));
  }

  private List<JsonObject> createItemLevelRequestsQueue(UUID firstInstanceId, UUID secondInstanceId) {
    String requestLevel = "Item";
    List<IndividualResource> createdRequests = new ArrayList<>();
    createdRequests.add(createRequest(firstInstanceId, UUID.randomUUID(), 1, requestLevel,
      OPEN_NOT_YET_FILLED));
    createdRequests.add(createRequest(firstInstanceId, UUID.randomUUID(), 2, requestLevel,
      OPEN_AWAITING_PICKUP));
    createdRequests.add(createRequest(firstInstanceId, UUID.randomUUID(), 3, requestLevel,
      OPEN_IN_TRANSIT));
    createdRequests.add(createRequest(firstInstanceId, UUID.randomUUID(), 1, requestLevel,
      OPEN_AWAITING_DELIVERY));
    createdRequests.add(createRequest(firstInstanceId, UUID.randomUUID(), 2, requestLevel,
      OPEN_AWAITING_PICKUP));
    createdRequests.add(createRequest(secondInstanceId, UUID.randomUUID(), 1, requestLevel,
      OPEN_IN_TRANSIT));
    createdRequests.add(createRequest(secondInstanceId, UUID.randomUUID(), 2, requestLevel,
      OPEN_AWAITING_DELIVERY));

    return createdRequests.stream()
      .map(IndividualResource::getJson)
      .collect(Collectors.toList());
  }

  private List<JsonObject> createTitleLevelRequestsQueue(UUID firstItemId, UUID secondItemId) {
    String requestLevel = "Title";
    List<IndividualResource> createdRequests = new ArrayList<>();
    createdRequests.add(createRequest(UUID.randomUUID(), firstItemId, 1, requestLevel,
      OPEN_NOT_YET_FILLED));
    createdRequests.add(createRequest(UUID.randomUUID(), firstItemId,2, requestLevel,
      OPEN_AWAITING_PICKUP));
    createdRequests.add(createRequest(UUID.randomUUID(), secondItemId, 1, requestLevel,
      OPEN_IN_TRANSIT));
    createdRequests.add(createRequest(UUID.randomUUID(), secondItemId, 2, requestLevel,
      OPEN_AWAITING_DELIVERY));

    return createdRequests.stream()
      .map(IndividualResource::getJson)
      .collect(Collectors.toList());
  }

  private IndividualResource createRequest(UUID instanceId, UUID itemId, int position,
    String requestLevel, String status) {

    DateTime requestDate = new DateTime(2021, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = new DateTime(2021, 7, 30, 0, 0, DateTimeZone.UTC);
    DateTime holdShelfExpirationDate = new DateTime(2021, 8, 31, 0, 0, DateTimeZone.UTC);

    try {
      return createEntity(
        new RequestRequestBuilder()
          .hold()
          .toHoldShelf()
          .withId(UUID.randomUUID())
          .withRequestDate(requestDate)
          .withItemId(itemId)
          .withRequesterId(UUID.randomUUID())
          .withProxyId(UUID.randomUUID())
          .withRequestExpirationDate(requestExpirationDate)
          .withHoldShelfExpirationDate(holdShelfExpirationDate)
          .withRequestLevel(requestLevel)
          .withHoldingsRecordId(UUID.randomUUID())
          .withInstanceId(instanceId)
          .withRequester("Jones", "Stuart", "Anthony", "6837502674015")
          .withProxy("Stuart", "Rebecca", "6059539205")
          .withStatus(status)
          .withPosition(position)
          .withPickupServicePointId(UUID.randomUUID())
          .withTags(new Tags().withTagList(asList("new", "important")))
          .create(), StorageTestSuite.storageUrl(REQUEST_STORAGE_URL));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private void checkResponse(int numberOfUpdates, JsonObject representation) {
    assertThat(representation.getInteger("numberOfUpdatedRequests"), is(numberOfUpdates));
    assertThat(representation.getString("status"), is(OPEN.toString()));
  }

  private TlrFeatureToggleJob createTlrFeatureToggleJob() {
    return createTlrFeatureToggleJobWithStatus(OPEN);
  }

  private TlrFeatureToggleJob createTlrFeatureToggleJobWithStatus(
    TlrFeatureToggleJob.Status status) {

    return new TlrFeatureToggleJob()
      .withStatus(status)
      .withMetadata(new Metadata()
        .withCreatedDate(new Date()));
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

  private Response getRequests() throws MalformedURLException, ExecutionException,
    InterruptedException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(StorageTestSuite.storageUrl(REQUEST_STORAGE_URL), TENANT_ID, json(getCompleted));

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

  private void stubTlrSettings(boolean isTlrEnabled) {
    final var tlrSettingsConfiguration = new TlrSettingsConfiguration(
      isTlrEnabled, false, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    StorageTestSuite.getWireMockServer().stubFor(WireMock.get(urlPathMatching(
      "/configurations/entries.*"))
      .willReturn(ok().withBody(mapFrom(tlrSettingsConfiguration).encodePrettily())));
  }
}
