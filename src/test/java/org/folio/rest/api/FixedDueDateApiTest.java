package org.folio.rest.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.HttpClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * @author shale
 *
 */
public class FixedDueDateApiTest {

  private static final String TABLE_NAME = "fixed_due_date_schedule";
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
  private static final String SCHEDULE_SECTION = "schedules";
  private static HttpClient client = new HttpClient(StorageTestSuite.getVertx());

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(dueDateURL());
  }

  @After
  public void checkIdsAfterEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    StorageTestSuite.checkForMismatchedIDs(TABLE_NAME);
  }

  @Test
  public void canCreateSchedule()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    //create simple empty fixed due date
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    UUID id = UUID.randomUUID();
    JsonObject fixDueDate = createFixedDueDate(id.toString(), "quarterly", null);
    client.post(dueDateURL(),
      fixDueDate, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));
    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    JsonObject representation = response.getJson();
    assertThat(representation.getString("id"), is(id.toString()));
    ////////////////////////////////////

    //update the fixed due date with a valid schedule
    CompletableFuture<Response> updateCompleted = new CompletableFuture<>();
    representation.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01", "2017-03-03", "2017-04-04")));
    client.put(dueDateURL("/"+representation.getString("id")),
      representation, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(updateCompleted));
    Response updateResponse = updateCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", representation.encodePrettily()),
      updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    ////////////////////////////////////////////////

    //update the fixed due date with a valid schedule
    CompletableFuture<Response> updateBad3Completed = new CompletableFuture<>();
    representation.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01", "2017-03-03", "2017-02-02")));
    client.put(dueDateURL("/"+representation.getString("id")),
      representation, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(updateBad3Completed));
    Response updateBad3Response = updateBad3Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", representation.encodePrettily()),
      updateBad3Response.getStatusCode(), is(422));
    ////////////////////////////////////////////////

    //update the fixed due date with an in-valid schedule
    CompletableFuture<TextResponse> updateBadCompleted = new CompletableFuture<>();
    representation.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-03-03", "2017-03-03", "2017-02-02")));
    client.put(dueDateURL("/"+representation.getString("id")),
      representation, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateBadCompleted));
    TextResponse updateBadResponse = updateBadCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", representation.encodePrettily()),
      updateBadResponse.getStatusCode(), is(422));
    ////////////////////////////////////////////////

    //update the fixed due date with a bad date in schedule
    CompletableFuture<TextResponse> updateBad2Completed = new CompletableFuture<>();
    fixDueDate.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-0303", "2017-03-03", "2017-02-02")));
    client.put(dueDateURL("/"+representation.getString("id")),
      fixDueDate, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateBad2Completed));
    TextResponse updateBad2Response = updateBad2Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate.encodePrettily()),
      updateBad2Response.getStatusCode(), is(422));
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
      response2.getStatusCode(), is(422));
    ////////////////////////////////////

    //create fixed due date without id, server generated
    CompletableFuture<JsonResponse> updateCompleted2 = new CompletableFuture<>();
    JsonObject fixDueDate3 = createFixedDueDate(null, "Semester", "desc");
    fixDueDate3.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01", "2017-04-04", "2017-02-12")));
    client.post(dueDateURL(),
      fixDueDate3, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted2));
    JsonResponse updateCompleted2Response = updateCompleted2.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate3.encodePrettily()),
      updateCompleted2Response.getStatusCode(), is(422));
    ////////////////////////////////////////////

    //create fixed due date without id, server generated
    CompletableFuture<JsonResponse> updateGoodCompleted = new CompletableFuture<>();
    JsonObject fixDueDate7 = createFixedDueDate(null, "Semester", "desc");
    fixDueDate7.put(SCHEDULE_SECTION,
      new JsonArray().add(createSchedule("2017-01-01", "2017-04-04", "2017-05-12")));
    client.post(dueDateURL(),
      fixDueDate7, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateGoodCompleted));
    JsonResponse updateCompleted5Response = updateGoodCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate7.encodePrettily()),
      updateCompleted5Response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    String newId = updateCompleted5Response.getJson().getString("id");
    ////////////////////////////////////////////

    //create duplicate name due date
    CompletableFuture<JsonResponse> updateCompleted3 = new CompletableFuture<>();
    JsonObject fixDueDate4 = createFixedDueDate(null, "Semester", "desc2");
    client.post(dueDateURL(),
      fixDueDate4, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted3));
    JsonResponse updateCompleted3Response = updateCompleted3.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate4.encodePrettily()),
      updateCompleted3Response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
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
      createdCompleted3Response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    String newId2 = createdCompleted3Response.getJson().getString("id");

    CompletableFuture<JsonResponse> updateCompleted4 = new CompletableFuture<>();
    JsonObject fixDueDate5 = createFixedDueDate(newId2, "Semester", "desc2");
    client.put(dueDateURL("/"+newId2),
      fixDueDate5, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted4));
    JsonResponse updateCompleted4Response = updateCompleted4.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", fixDueDate5.encodePrettily()),
      updateCompleted4Response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    ////////////////////////////////////////////

    /////////sort alpha numeric desc / asc /////
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    URL url1 = dueDateURL("?query=name=*"+URLEncoder.encode(" sortBy name/sort.descending", "UTF-8"));
    System.out.println(url1.toString());
    client.get(url1, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));
    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", getResponse.getJson().encodePrettily()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getJsonArray("fixedDueDateSchedules").size(), is(3));
    assertThat(
      getResponse.getJson().getJsonArray("fixedDueDateSchedules").getJsonObject(0).getString("name"),
      is("semester2"));

    CompletableFuture<JsonResponse> getCompleted2 = new CompletableFuture<>();
    URL url2 = dueDateURL("?query=name=*"+URLEncoder.encode(" sortBy name/sort.ascending", "UTF-8"));
    System.out.println(url2.toString());
    client.get(url2, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted2));
    JsonResponse getResponse2 = getCompleted2.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", getResponse2.getJson().encodePrettily()),
      getResponse2.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse2.getJson().getJsonArray("fixedDueDateSchedules").size(), is(3));
    assertThat(
      getResponse2.getJson().getJsonArray("fixedDueDateSchedules").getJsonObject(0).getString("name"),
      is("quarterly"));
    /////////////////////////////////////////////////////////

    //// get by id ///////////////////////
    CompletableFuture<JsonResponse> getCompleted4 = new CompletableFuture<>();
    client.get(dueDateURL("/"+newId2), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted4));
    JsonResponse getCompleted4Response = getCompleted4.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", getCompleted4Response.getJson().encodePrettily()),
      getCompleted4Response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getCompleted4Response.getJson().getString("name"), is("semester2"));

    //// delete by id ///////////////////////
    CompletableFuture<Response> delCompleted = new CompletableFuture<>();
    client.delete(dueDateURL("/"+newId2), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delCompleted));
    Response delCompleted4Response = delCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", dueDateURL("/"+newId2)),
      delCompleted4Response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    //// get by bad id ///////////////////////
    CompletableFuture<TextResponse> getCompleted5 = new CompletableFuture<>();
    client.get(dueDateURL("/12345"), StorageTestSuite.TENANT_ID,
      ResponseHandler.text(getCompleted5));
    TextResponse getCompleted5Response = getCompleted5.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", getCompleted5Response.getBody()),
      getCompleted5Response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
    System.out.println(dueDateURL("/12345") + " " + getCompleted5Response.getBody());

    //// delete by bad id ///////////////////////
    CompletableFuture<TextResponse> delCompleted5 = new CompletableFuture<>();
    client.delete(dueDateURL("/12345"), StorageTestSuite.TENANT_ID,
      ResponseHandler.text(delCompleted5));
    TextResponse delCompleted5Response = delCompleted5.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", delCompleted5Response.getBody()),
      delCompleted5Response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
    System.out.println(dueDateURL("/12345") + " " + delCompleted5Response.getBody());

    //update by bad id
    CompletableFuture<TextResponse> updateBadCompleted4 = new CompletableFuture<>();
    JsonObject updateDueDate5 = createFixedDueDate("12345", "Semester", "desc3");
    client.put(dueDateURL("/12345"),
      updateDueDate5, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateBadCompleted4));
    TextResponse updateBadCompleted4Response = updateBadCompleted4.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", updateDueDate5.encodePrettily()),
      updateBadCompleted4Response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    //// get , should have 2 records ///////////////////////
    CompletableFuture<JsonResponse> get2Completed = new CompletableFuture<>();
    client.get(dueDateURL(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(get2Completed));
    JsonResponse get2CompletedResponse = get2Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", get2CompletedResponse.getJson().encodePrettily()),
      get2CompletedResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(get2CompletedResponse.getJson().getJsonArray("fixedDueDateSchedules").size(), is(2));
  }

  private static URL dueDateURL() throws MalformedURLException {
    return dueDateURL("");
  }

  private static URL dueDateURL(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/fixed-due-date-schedule-storage/fixed-due-date-schedules" + subPath);
  }

  private JsonObject createSchedule(String from, String to, String due){
    JsonObject jo = new JsonObject();
    if(from != null){
      jo.put("from", from);
    }
    if(to != null){
      jo.put("to", to);
    }
    if(from != null){
      jo.put("due", due);
    }
    return jo;
  }

  private JsonObject createFixedDueDate(String id, String name, String desc, JsonArray schedules){
    JsonObject jo = createFixedDueDate(id, name, desc);
    jo.put("schedules", schedules);
    return jo;
  }

  private JsonObject createFixedDueDate(String id, String name, String desc){
    JsonObject jo = new JsonObject();
    if(id != null){
      jo.put("id", id);
    }
    if(name != null){
      jo.put("name", name);
    }
    if(desc != null){
      jo.put("description", desc);
    }
    return jo;
  }
}
