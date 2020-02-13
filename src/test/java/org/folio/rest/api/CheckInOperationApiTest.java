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
import org.folio.rest.support.builders.CheckInOperationBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class CheckInOperationApiTest extends ApiTests {
  private static final String TABLE_NAME = "check_in_operation";

  private final AssertingRecordClient checkInOperationClient =
    new AssertingRecordClient(client, StorageTestSuite.TENANT_ID,
      InterfaceUrls::checkInOperationStorageUrl, "checkInOperations");

  @Before
  public void beforeEach() {
    StorageTestSuite.cleanUpTable(TABLE_NAME);
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs(TABLE_NAME);
  }

  @Test
  public void canCreateCheckInOperation() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInOperationToCreate = createSampleCheckInOperation().create();

    IndividualResource createResult = checkInOperationClient.create(checkInOperationToCreate);

    assertThat(createResult.getJson(), is(checkInOperationToCreate));
  }


  @Test
  public void cannotCreateCheckInOperationIfRequiredPropertyMissing() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInOperationToCreate = new CheckInOperationBuilder().create();

    JsonResponse createResponse = checkInOperationClient.attemptCreate(checkInOperationToCreate);

    assertThat(createResponse, isValidationResponseWhich(hasMessage("may not be null")));
    assertThat(createResponse, isValidationResponseWhich(hasParameter("occurredDateTime", "null")));
  }

  @Test
  public void canDeleteIndividualCheckInOperation() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInOperationToCreate = createSampleCheckInOperation()
      .create();

    IndividualResource createResponse = checkInOperationClient.create(checkInOperationToCreate);

    checkInOperationClient.delete(createResponse);
    JsonResponse getResponse = checkInOperationClient.attemptGetById(
      UUID.fromString(createResponse.getId()));

    assertThat(getResponse.getStatusCode(), is(404));
  }

  @Test
  public void cannotDeleteCheckInOperationWhichDoesNotExist() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    TextResponse deleteResponse = checkInOperationClient
      .attemptDeleteById(UUID.randomUUID());

    assertThat(deleteResponse.getStatusCode(), is(404));
  }

  @Test
  public void canSearchForCheckInOperationsByOccurredDateTime()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    final DateTime beforeCreateDateTime = DateTime.now(DateTimeZone.UTC);

    JsonObject firstCheckInOperation = createSampleCheckInOperation().create();
    JsonObject secondCheckInOperation = createSampleCheckInOperation().create();
    JsonObject thirdCheckInOperation = createSampleCheckInOperation().create();

    checkInOperationClient.create(firstCheckInOperation);
    checkInOperationClient.create(secondCheckInOperation);
    checkInOperationClient.create(thirdCheckInOperation);

    MultipleRecords<JsonObject> allCheckInOperations = checkInOperationClient
      .getMany(String.format("occurredDateTime >= '%s'", beforeCreateDateTime));

    assertThat(allCheckInOperations.getTotalRecords(), is(3));
    assertTrue(allCheckInOperations.getRecords().contains(firstCheckInOperation));
    assertTrue(allCheckInOperations.getRecords().contains(secondCheckInOperation));
    assertTrue(allCheckInOperations.getRecords().contains(thirdCheckInOperation));
  }

  @Test
  public void cannotUseNegativeOffsetForSearch()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    JsonResponse jsonResponse = checkInOperationClient
      .attemptGetMany("occurredDateTime is not null", -10, 10);

    assertThat(jsonResponse.getStatusCode(), is(400));
    assertThat(jsonResponse.getBody().trim(),
      matchesPattern("'offset' .+ must be greater than or equal to 0"));
  }


  private CheckInOperationBuilder createSampleCheckInOperation() {
    return new CheckInOperationBuilder()
      .withOccurredDateTime(DateTime.now(DateTimeZone.UTC))
      .withItemId(UUID.randomUUID())
      .withCheckInServicePointId(UUID.randomUUID())
      .withPerformedByUserId(UUID.randomUUID());
  }
}
