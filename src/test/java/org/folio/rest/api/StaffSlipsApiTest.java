package org.folio.rest.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.*;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.collection.IsArrayContainingInAnyOrder.arrayContainingInAnyOrder;
import static org.hamcrest.core.Is.*;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.StaffSlipRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class StaffSlipsApiTest extends ApiTests {

  private static final String TEST_STAFF_SLIP_1_NAME = "Test Staff Slip 1";
  private static final String TEST_STAFF_SLIP_1_DESCRIPTION = "Test Staff Slip 1 Description";
  private static final String TEST_STAFF_SLIP_1_DESCRIPTION_ALTERNATE = "Test Staff Slip 1 Description Updated";
  private static final String TEST_STAFF_SLIP_1_Template = "Test Staff Slip 1 Template";

  private static final String ID_KEY = "id";
  private static final String ACTIVE_KEY = "active";
  private static final String NAME_KEY = "name";
  private static final String DESCRIPTON_KEY = "description";
  private static final String TEMPLATE_KEY = "template";

  private static AtomicBoolean isRefTestDone = new AtomicBoolean(false);

  @Before
  public void beforeEach() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    // check reference data once before deletion
    synchronized (this) {
      if (!isRefTestDone.get()) {
        canGetStaffSlipReferenceData();
        isRefTestDone.set(true);
      }
    }
    StorageTestSuite.deleteAll(staffSlipsStorageUrl());
  }

  private void canGetStaffSlipReferenceData()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(staffSlipsStorageUrl(""), StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonArray slipsJsonArray = getResponse.getJson().getJsonArray("staffSlips");
    Object [] names = slipsJsonArray.stream().map(o -> ((JsonObject) o).getString(NAME_KEY)).toArray();
    assertThat(names, arrayContainingInAnyOrder("Hold", "Transit", "Request delivery", "Pick slip"));
  }

  /* Begin Tests */

  @Test
  public void canCreateAStaffSlip()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    JsonResponse creationResponse = makeStaffSlip(new StaffSlipRequestBuilder().withName(TEST_STAFF_SLIP_1_NAME)
      .withDescription(TEST_STAFF_SLIP_1_DESCRIPTION).withTemplate(TEST_STAFF_SLIP_1_Template).create());

    assertThat(creationResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    assertThat(creationResponse.getJson().getString(ID_KEY), notNullValue());
    assertThat(creationResponse.getJson().getBoolean(ACTIVE_KEY), is(true));
    assertThat(creationResponse.getJson().getString(NAME_KEY), is(TEST_STAFF_SLIP_1_NAME));
    assertThat(creationResponse.getJson().getString(DESCRIPTON_KEY), is(TEST_STAFF_SLIP_1_DESCRIPTION));
    assertThat(creationResponse.getJson().getString(TEMPLATE_KEY), is(TEST_STAFF_SLIP_1_Template));

  }

  @Test
  public void cannotCreateAStaffSlipWithoutAName()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    JsonResponse creationResponse = makeStaffSlip(new StaffSlipRequestBuilder().withName(null).create());

    assertThat(String.format("Creating the loan should fail: %s", creationResponse.getBody()),
      creationResponse, isUnprocessableEntity());

  }

  @Test
  public void canCreateAnInactiveStaffSlip()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    JsonResponse creationResponse = makeStaffSlip(new StaffSlipRequestBuilder().withActive(false).create());

    assertThat(creationResponse.getJson().getBoolean(ACTIVE_KEY), is(false));

  }

  @Test
  public void cannotCreateStaffSlipWithDuplicateName()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    makeStaffSlip(new StaffSlipRequestBuilder().withName(TEST_STAFF_SLIP_1_NAME).create());

    JsonResponse creationAttemptResponse = makeStaffSlip(
      new StaffSlipRequestBuilder().withName(TEST_STAFF_SLIP_1_NAME).create());

    assertThat(creationAttemptResponse, isBadRequest());

  }

  @Test
  public void canGetStaffSlipById()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    JsonResponse creationResponse = makeStaffSlip(new StaffSlipRequestBuilder().create());

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    String slipId = creationResponse.getJson().getString(ID_KEY);

    client.get(staffSlipsStorageUrl("/" + slipId), StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString(ID_KEY), is(slipId));

  }

  @Test
  public void canQueryStaffSlip()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    JsonResponse creationResponse = makeStaffSlip(new StaffSlipRequestBuilder().create());

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    String slipId = creationResponse.getJson().getString(ID_KEY);

    URL path = staffSlipsStorageUrl("?query=" + URLEncoder.encode(String.format("%s==\"%s\"", ID_KEY, slipId), UTF_8));

    client.get(path, StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(10, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonArray slipsJsonArray = getResponse.getJson().getJsonArray("staffSlips");

    assertThat(slipsJsonArray.getJsonObject(0).getString(ID_KEY), is(slipId));

  }

  @Test
  public void canUpdateStaffSlipById()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    UUID slipId = UUID.randomUUID();

    JsonResponse creationResponse = makeStaffSlip(
      new StaffSlipRequestBuilder().withId(slipId).withName(TEST_STAFF_SLIP_1_NAME)
        .withDescription(TEST_STAFF_SLIP_1_DESCRIPTION).withTemplate(TEST_STAFF_SLIP_1_Template).create());

    assertThat(String.format("%s", creationResponse.getBody()), creationResponse.getStatusCode(),
      is(HttpURLConnection.HTTP_CREATED));

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    JsonObject updatedSlip = new StaffSlipRequestBuilder().withId(slipId).withName(TEST_STAFF_SLIP_1_NAME)
      .withDescription(TEST_STAFF_SLIP_1_DESCRIPTION_ALTERNATE).create();

    client.put(staffSlipsStorageUrl("/" + slipId), updatedSlip, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(putCompleted));

    Response putReponse = putCompleted.get(5, TimeUnit.SECONDS);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(staffSlipsStorageUrl("/" + slipId), StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putReponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(getResponse.getJson().getString(DESCRIPTON_KEY), is(TEST_STAFF_SLIP_1_DESCRIPTION_ALTERNATE));

  }

  @Test
  public void canDeleteSlipById()
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    UUID slipId = UUID.randomUUID();

    makeStaffSlip(new StaffSlipRequestBuilder().withId(slipId).withName(TEST_STAFF_SLIP_1_NAME)
      .withDescription(TEST_STAFF_SLIP_1_DESCRIPTION).withTemplate(TEST_STAFF_SLIP_1_Template).create());

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(staffSlipsStorageUrl("/" + slipId), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(deleteCompleted));

    Response deleteReponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(staffSlipsStorageUrl("/" + slipId), StorageTestSuite.TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteReponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private JsonResponse makeStaffSlip(JsonObject staffSlipJson)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(staffSlipsStorageUrl(), staffSlipJson, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  private URL staffSlipsStorageUrl() throws MalformedURLException {
    return staffSlipsStorageUrl("");
  }

  private URL staffSlipsStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/staff-slips-storage/staff-slips" + subPath);
  }

}
