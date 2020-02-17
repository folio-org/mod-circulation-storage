package org.folio.rest.api;

import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.rest.support.matchers.ValidationResponseMatchers.isValidationResponseWhich;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.MultipleRecords;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.CheckInBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class CheckInStorageApiTest extends ApiTests {
  private static final String TABLE_NAME = "check_in";

  private final AssertingRecordClient checkInClient =
    new AssertingRecordClient(client, StorageTestSuite.TENANT_ID,
      InterfaceUrls::checkInsStorageUrl, "checkIns");

  @Before
  public void beforeEach() {
    StorageTestSuite.cleanUpTable(TABLE_NAME);
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs(TABLE_NAME);
  }

  @Test
  public void canCreateCheckIn() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInToCreate = createSampleCheckIn().create();

    IndividualResource createResult = checkInClient.create(checkInToCreate);

    assertThat(createResult.getJson(), is(checkInToCreate));
  }


  @Test
  public void cannotCreateCheckInIfRequiredPropertyMissing() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInToCreate = new CheckInBuilder().create();

    JsonResponse createResponse = checkInClient.attemptCreate(checkInToCreate);

    assertThat(createResponse, isValidationResponseWhich(hasMessage("may not be null")));
    assertThat(createResponse, isValidationResponseWhich(hasParameter("occurredDateTime", "null")));
  }

  @Test
  public void canDeleteIndividualCheckIn() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInToCreate = createSampleCheckIn().create();

    IndividualResource createResponse = checkInClient.create(checkInToCreate);

    checkInClient.delete(createResponse);
    JsonResponse getResponse = checkInClient
      .attemptGetById(UUID.fromString(createResponse.getId()));

    assertThat(getResponse.getStatusCode(), is(404));
  }

  @Test
  public void cannotDeleteCheckInWhichDoesNotExist() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    TextResponse deleteResponse = checkInClient
      .attemptDeleteById(UUID.randomUUID());

    assertThat(deleteResponse.getStatusCode(), is(404));
  }

  @Test
  public void canSearchForCheckInsByOccurredDateTime()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    final DateTime beforeCreateDateTime = DateTime.now(DateTimeZone.UTC);

    JsonObject firstCheckIn = createSampleCheckIn().create();
    JsonObject secondCheckIn = createSampleCheckIn().create();
    JsonObject thirdCheckIn = createSampleCheckIn().create();

    checkInClient.create(firstCheckIn);
    checkInClient.create(secondCheckIn);
    checkInClient.create(thirdCheckIn);

    MultipleRecords<JsonObject> allCheckInOperations = checkInClient
      .getMany(String.format("occurredDateTime >= %s", beforeCreateDateTime.getMillis()));

    assertThat(allCheckInOperations.getTotalRecords(), is(3));
    assertTrue(allCheckInOperations.getRecords().contains(firstCheckIn));
    assertTrue(allCheckInOperations.getRecords().contains(secondCheckIn));
    assertTrue(allCheckInOperations.getRecords().contains(thirdCheckIn));
  }

  @Test
  public void cannotUseNegativeOffsetForSearch()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    JsonResponse jsonResponse = checkInClient
      .attemptGetMany("occurredDateTime is not null", -10, 10);

    assertThat(jsonResponse.getStatusCode(), is(400));
    assertThat(jsonResponse.getBody().trim(),
      matchesPattern("'offset' .+ must be greater than or equal to 0"));
  }


  private CheckInBuilder createSampleCheckIn() {
    return new CheckInBuilder()
      .withOccurredDateTime(DateTime.now(DateTimeZone.UTC))
      .withItemId(UUID.randomUUID())
      .withServicePointId(UUID.randomUUID())
      .withPerformedByUserId(UUID.randomUUID());
  }
}
