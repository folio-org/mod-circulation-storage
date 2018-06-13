package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.StaffSlipRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class StaffSlipsApiTest extends ApiTests {
	  
  private static final String TEST_STAFF_SLIP_1_NAME = "Test Staff Slip 1";
  private static final String TEST_STAFF_SLIP_1_DESCRIPTION = "Test Staff Slip 1 Description";
  private static final String TEST_STAFF_SLIP_1_Template = "Test Staff Slip 1 Template";
	
  @Before
  public void beforeEach()
    throws MalformedURLException {
    StorageTestSuite.deleteAll(staffSlipsStorageUrl());
  }

  /* Begin Tests */
  
  @Test
  public void canCreateAStaffSlip()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
	  CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
	  
	  JsonObject creationRequest = new StaffSlipRequestBuilder()
	    .withName(TEST_STAFF_SLIP_1_NAME)
	    .withDescription(TEST_STAFF_SLIP_1_DESCRIPTION)
	    .withTemplate(TEST_STAFF_SLIP_1_Template)
	    .create();
	  
	  client.post(staffSlipsStorageUrl(), creationRequest, StorageTestSuite.TENANT_ID,
		      ResponseHandler.json(createCompleted));
	  
	  JsonResponse creationResponse = createCompleted.get(5, TimeUnit.SECONDS);
	  
	  assertThat(creationResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
	  
//	  assertThat(creationResponse.getJson().getString("id"), notNullValue());
//	  assertThat(creationResponse.getJson().getBoolean("active"), is(true));
//	  assertThat(creationResponse.getJson().getBoolean("name"), is(TEST_STAFF_SLIP_1_NAME));
//	  assertThat(creationResponse.getJson().getBoolean("description"), is(TEST_STAFF_SLIP_1_DESCRIPTION));
//	  assertThat(creationResponse.getJson().getBoolean("template"), is(TEST_STAFF_SLIP_1_Template));
	  
  }

  @Test
  public void canCreateAStaffSlipWithoutAnId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  @Test
  public void cannotCreateAStaffSlipWithoutAName()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  @Test
  public void canCreateAnInactiveStaffSlip()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  @Test
  public void cannotCreateStaffSlipWithDuplicateName()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  @Test
  public void canGetStaffSlipById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  @Test
  public void canQueryStaffSlips()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  @Test
  public void canUpdateStaffSlipById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  @Test
  public void canDeleteSlipById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  @Test
  public void canDeleteAllStaffSlips()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
	  
  }
  
  private URL staffSlipsStorageUrl() throws MalformedURLException {
    return staffSlipsStorageUrl("");
  }
  
  private URL staffSlipsStorageUrl(String subPath)
    throws MalformedURLException {
    return StorageTestSuite.storageUrl("/staff-slips-storage/staff-slips" + subPath);
  }
  
}
