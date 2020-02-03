package org.folio.rest.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.folio.rest.support.*;
import org.hamcrest.junit.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.isBadRequest;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.isCreated;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.isNotFound;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.isOk;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.isUnprocessableEntity;
import static org.folio.rest.api.LoanPoliciesApiTest.loanPolicyStorageUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author shale
 *
 */
public class FixedDueDateApiTest extends ApiTests {
  private static final String TABLE_NAME = "fixed_due_date_schedule";
  static final String SCHEDULE_SECTION = "schedules";

  @Before
  public void beforeEach()
    throws MalformedURLException {

    StorageTestSuite.deleteAll(loanPolicyStorageUrl());
    StorageTestSuite.deleteAll(dueDateURL());
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs(TABLE_NAME);
  }

  @Test
  public void canCreateSchedule()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    //create simple empty fixed due date
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    UUID id = UUID.randomUUID();
    JsonObject fixDueDate = createFixedDueDate(id.toString(), "quarterly", null);
    client.post(dueDateURL(),
      fixDueDate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));
    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", response.getBody()),
      response.getStatusCode(), isCreated());
    //assertThat(response, isOkapiCreated());
    JsonObject representation = response.getJson();
    assertThat(representation.getString("id"), is(id.toString()));
    ////////////////////////////////////
    representation.remove("metadata");

    //update the fixed due date with a valid schedule
    CompletableFuture<Response> updateCompleted = new CompletableFuture<>();
    representation.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01T10:00:00.000+0000",
        "2017-03-03T10:00:00.000+0000", "2017-04-04T10:00:00.000+0000")));
    client.put(dueDateURL("/"+representation.getString("id")),
      representation, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(updateCompleted));
    Response updateResponse = updateCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", representation.encodePrettily()),
      updateResponse.getStatusCode(), isNoContent());
    ////////////////////////////////////////////////

    //update the fixed due date with a valid schedule
    CompletableFuture<Response> updateBad3Completed = new CompletableFuture<>();
    representation.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01T10:00:00.000+0000",
        "2017-03-03T10:00:00.000+0000", "2017-02-02T10:00:00.000+0000")));
    client.put(dueDateURL("/"+representation.getString("id")),
      representation, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(updateBad3Completed));
    Response updateBad3Response = updateBad3Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", representation.encodePrettily()),
      updateBad3Response.getStatusCode(), isUnprocessableEntity());
    ////////////////////////////////////////////////

    //update the fixed due date with an in-valid schedule
    CompletableFuture<TextResponse> updateBadCompleted = new CompletableFuture<>();
    representation.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-03-03T10:00:00.000+0000",
        "2017-03-03T10:00:00.000+0000", "2017-02-02T10:00:00.000+0000")));
    client.put(dueDateURL("/"+representation.getString("id")),
      representation, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateBadCompleted));
    TextResponse updateBadResponse = updateBadCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", representation.encodePrettily()),
      updateBadResponse.getStatusCode(), isUnprocessableEntity());
    ////////////////////////////////////////////////

    //update the fixed due date with a bad date in schedule
    CompletableFuture<TextResponse> updateBad2Completed = new CompletableFuture<>();
    fixDueDate.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-0303T10:00:00.000+0000",
        "2017-03-03T10:00:00.000+0000", "2017-02-02T10:00:00.000+0000")));
    client.put(dueDateURL("/"+representation.getString("id")),
      fixDueDate, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateBad2Completed));
    TextResponse updateBad2Response = updateBad2Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate.encodePrettily()),
      updateBad2Response.getStatusCode(), isBadRequest());
    ////////////////////////////////////////////////

    //try to create fixed due date without a mandatory name field
    CompletableFuture<JsonResponse> createCompleted2 = new CompletableFuture<>();
    UUID id2 = UUID.randomUUID();
    JsonObject fixDueDate2 = createFixedDueDate(id2.toString(), null, null);
    client.post(dueDateURL(),
      fixDueDate2, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted2));
    JsonResponse response2 = createCompleted2.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", response2.getBody()),
      response2.getStatusCode(), isUnprocessableEntity());
    ////////////////////////////////////

    //create fixed due date without id, server generated
    CompletableFuture<JsonResponse> updateCompleted2 = new CompletableFuture<>();
    JsonObject fixDueDate3 = createFixedDueDate(null, "Semester", "desc");
    fixDueDate3.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01T10:00:00.000+0000",
        "2017-04-04T10:00:00.000+0000", "2017-02-12T10:00:00.000+0000")));
    client.post(dueDateURL(),
      fixDueDate3, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted2));
    JsonResponse updateCompleted2Response = updateCompleted2.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate3.encodePrettily()),
      updateCompleted2Response.getStatusCode(), isUnprocessableEntity());
    ////////////////////////////////////////////

    //create fixed due date without id, server generated
    CompletableFuture<JsonResponse> updateGoodCompleted = new CompletableFuture<>();
    JsonObject fixDueDate7 = createFixedDueDate(null, "Semester", "desc");
    fixDueDate7.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01T10:00:00.000+0000",
        "2017-04-04T10:00:00.000+0000", "2017-05-12T10:00:00.000+0000")));
    client.post(dueDateURL(),
      fixDueDate7, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateGoodCompleted));
    JsonResponse updateCompleted5Response = updateGoodCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate7.encodePrettily()),
      updateCompleted5Response.getStatusCode(), isCreated());
    fixDueDate7 = updateCompleted5Response.getJson();
    fixDueDate7.remove("metadata");
    String newId = fixDueDate7.getString("id");
    ////////////////////////////////////////////

    //update the fixed due date with a valid schedule
    CompletableFuture<Response> updateBad4Completed = new CompletableFuture<>();
    fixDueDate7.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01T10:00:00.000+0000",
        "2017-01-01T10:00:00.000+0000", "2017-01-01T10:00:00.000+0000")));
    client.put(dueDateURL("/"+newId),
      fixDueDate7, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(updateBad4Completed));
    Response updateBad4Response = updateBad4Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate7.encodePrettily()),
      updateBad4Response.getStatusCode(), isNoContent());
    ////////////////////////////////////////////////

    //update the fixed due date with a valid schedule
    CompletableFuture<Response> updateBad5Completed = new CompletableFuture<>();
    fixDueDate7.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01T10:00:00.000+0000",
        "2017-02-02T10:00:00.000+0000", "2017-02-02T10:00:00.000+0000")));
    client.put(dueDateURL("/"+newId),
      fixDueDate7, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(updateBad5Completed));
    Response updateBad5Response = updateBad5Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate7.encodePrettily()),
      updateBad5Response.getStatusCode(), isNoContent());
    ////////////////////////////////////////////////

    //create duplicate name due date
    CompletableFuture<JsonResponse> updateCompleted3 = new CompletableFuture<>();
    JsonObject fixDueDate4 = createFixedDueDate(null, "Semester", "desc2");
    client.post(dueDateURL(),
      fixDueDate4, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted3));
    JsonResponse updateCompleted3Response = updateCompleted3.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate4.encodePrettily()),
      updateCompleted3Response.getStatusCode(), isUnprocessableEntity());
    ////////////////////////////////////////////

    ////////////////////////////////////////////
    //create and then update with a duplicate name due date
    CompletableFuture<JsonResponse> createCompleted3 = new CompletableFuture<>();
    JsonObject fixDueDate6 = createFixedDueDate(null, "semester2", "desc2");
    client.post(dueDateURL(),
      fixDueDate6, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted3));
    JsonResponse createdCompleted3Response = createCompleted3.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s",
      createdCompleted3Response.getJson().encodePrettily()),
      createdCompleted3Response.getStatusCode(), isCreated());
    String newId2 = createdCompleted3Response.getJson().getString("id");

    ////////////////////////////////////////////
    //get with non-existing field now returns 200 / OK
    CompletableFuture<JsonResponse> getCQLCompleted2 = new CompletableFuture<>();
    URL url3 = dueDateURL("?query=name=fielddoesntexist=hi");
    System.out.println(url3.toString());
    client.get(url3, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCQLCompleted2));
    JsonResponse getCQLResponse2 = getCQLCompleted2.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get schedule: %s", getCQLResponse2.getJson().encodePrettily()),
      getCQLResponse2.getStatusCode(), isOk());
    //////////////////////////////////////////////////////////

    //// get by id ///////////////////////
    CompletableFuture<JsonResponse> getCompleted4 = new CompletableFuture<>();
    client.get(dueDateURL("/"+newId2), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted4));
    JsonResponse getCompleted4Response = getCompleted4.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", getCompleted4Response.getJson().encodePrettily()),
      getCompleted4Response.getStatusCode(), isOk());
    assertThat(getCompleted4Response.getJson().getString("name"), is("semester2"));

    //// delete by id ///////////////////////
    CompletableFuture<Response> delCompleted = new CompletableFuture<>();
    client.delete(dueDateURL("/"+newId2), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delCompleted));
    Response delCompleted4Response = delCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", dueDateURL("/"+newId2)),
      delCompleted4Response.getStatusCode(), isNoContent());

    //// get by bad id ///////////////////////
    CompletableFuture<TextResponse> getCompleted5 = new CompletableFuture<>();
    client.get(dueDateURL("/12345"), StorageTestSuite.TENANT_ID,
      ResponseHandler.text(getCompleted5));
    TextResponse getCompleted5Response = getCompleted5.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", getCompleted5Response.getBody()),
      getCompleted5Response.getStatusCode(), isNotFound());
    System.out.println(dueDateURL("/12345") + " " + getCompleted5Response.getBody());

    //update by bad id
    CompletableFuture<TextResponse> updateBadCompleted4 = new CompletableFuture<>();
    JsonObject updateDueDate5 = createFixedDueDate("12345", "Semester", "desc3");
    client.put(dueDateURL("/12345"),
      updateDueDate5, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateBadCompleted4));
    TextResponse updateBadCompleted4Response = updateBadCompleted4.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", updateDueDate5.encodePrettily()),
      updateBadCompleted4Response.getStatusCode(), isUnprocessableEntity());

    //// get , should have 2 records ///////////////////////
    CompletableFuture<JsonResponse> get2Completed = new CompletableFuture<>();
    client.get(dueDateURL(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get2Completed));
    JsonResponse get2CompletedResponse = get2Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", get2CompletedResponse.getJson().encodePrettily()),
      get2CompletedResponse.getStatusCode(), isOk());
    assertThat(get2CompletedResponse.getJson().getJsonArray("fixedDueDateSchedules").size(), is(2));

    //// try to delete all fdds (uses cascade so will succeed) ///////////////////////
    CompletableFuture<Response> delAllCompleted = new CompletableFuture<>();
    client.delete(dueDateURL(), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delAllCompleted));
    Response delAllCompleted4Response = delAllCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", dueDateURL()),
      delAllCompleted4Response.getStatusCode(), isNoContent());
    ////////////////////////////////////////////////////////

    //// get , should have 0 records ///////////////////////
    CompletableFuture<JsonResponse> get3Completed = new CompletableFuture<>();
    client.get(dueDateURL(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get3Completed));
    JsonResponse get3CompletedResponse = get3Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to get due date: %s", get3CompletedResponse.getJson().encodePrettily()),
      get3CompletedResponse.getStatusCode(), isOk());
    assertThat(get3CompletedResponse.getJson().getJsonArray("fixedDueDateSchedules").size(), is(0));
  }

  @Ignore("Fails on Mac OS due to differences in UTF-8 collation libraries")
  @Test
  public void canSortDifferentCaseNamesAscending()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    createFixedDueDateSchedule(createFixedDueDate("quarterly"));
    createFixedDueDateSchedule(createFixedDueDate("Semester"));
    createFixedDueDateSchedule(createFixedDueDate("semester2"));

    URL sortUrl = dueDateURL("?query=name=*"
      + URLEncoder.encode(" sortBy name/sort.ascending", "UTF-8"));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(sortUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get fixed due date schedules: %s",
      getResponse.getJson().encodePrettily()),
      getResponse.getStatusCode(), isOk());

    List<JsonObject> results = JsonArrayHelper.toList(getResponse.getJson()
      .getJsonArray("fixedDueDateSchedules"));

    results.stream()
      .map(result -> result.getString("name"))
      .forEachOrdered(System.out::println);

    assertThat(results.size(), is(3));
    assertThat(results.get(0).getString("name"), is("quarterly"));
  }

  @Test
  public void canSortByNameAscending()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    createFixedDueDateSchedule(createFixedDueDate("quarterly"));
    createFixedDueDateSchedule(createFixedDueDate("semester"));
    createFixedDueDateSchedule(createFixedDueDate("semester2"));

    URL sortUrl = dueDateURL("?query=name=*"
      + URLEncoder.encode(" sortBy name/sort.ascending", "UTF-8"));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(sortUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get fixed due date schedules: %s",
      getResponse.getJson().encodePrettily()),
      getResponse.getStatusCode(), isOk());

    List<JsonObject> results = JsonArrayHelper.toList(getResponse.getJson()
      .getJsonArray("fixedDueDateSchedules"));

    results.stream()
      .map(result -> result.getString("name"))
      .forEachOrdered(System.out::println);

    assertThat(results.size(), is(3));
    assertThat(results.get(0).getString("name"), is("quarterly"));
    assertThat(results.get(2).getString("name"), is("semester2"));
  }

  @Test
  public void canSortByNameDescending()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    createFixedDueDateSchedule(createFixedDueDate("quarterly"));
    createFixedDueDateSchedule(createFixedDueDate("semester"));
    createFixedDueDateSchedule(createFixedDueDate("semester2"));

    URL sortUrl = dueDateURL("?query=name=*"
      + URLEncoder.encode(" sortBy name/sort.descending", "UTF-8"));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(sortUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get fixed due date schedules: %s",
      getResponse.getJson().encodePrettily()),
      getResponse.getStatusCode(), isOk());

    List<JsonObject> results = JsonArrayHelper.toList(getResponse.getJson()
      .getJsonArray("fixedDueDateSchedules"));

    assertThat(results.size(), is(3));
    assertThat(results.get(0).getString("name"), is("semester2"));
    assertThat(results.get(2).getString("name"), is("quarterly"));
  }

  protected static URL dueDateURL() throws MalformedURLException {
    return dueDateURL("");
  }

  protected static URL dueDateURL(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl(
      "/fixed-due-date-schedule-storage/fixed-due-date-schedules" + subPath);
  }

  protected static JsonObject createSchedule(
    String from,
    String to,
    String due) {

    JsonObject jo = new JsonObject();

    if(from != null) {
      jo.put("from", from);
    }
    if(to != null) {
      jo.put("to", to);
    }
    if(from != null) {
      jo.put("due", due);
    }
    return jo;
  }

  protected static JsonObject createFixedDueDate(
    String id,
    String name,
    String desc) {

    JsonObject jo = new JsonObject();

    if(id != null) {
      jo.put("id", id);
    }

    if(name != null) {
      jo.put("name", name);
    }

    if(desc != null) {
      jo.put("description", desc);
    }

    return jo;
  }

  protected static JsonObject createFixedDueDate(String name) {
    return createFixedDueDate(UUID.randomUUID().toString(), name, "");
  }

  private IndividualResource createFixedDueDateSchedule(JsonObject request)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(dueDateURL(), request, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat(String.format("Failed to create fixed due date: %s", response.getBody()),
      response.getStatusCode(), isCreated());

    return new IndividualResource(response);
  }
}
