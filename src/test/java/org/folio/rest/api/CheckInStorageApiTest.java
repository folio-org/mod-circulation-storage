package org.folio.rest.api;

import static org.folio.rest.support.matchers.DomainEventAssertions.assertCreateEventForCheckIn;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertNoCheckInEvent;
import static org.folio.rest.support.matchers.JsonMatchers.hasSameProperties;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.rest.support.matchers.ValidationResponseMatchers.isValidationResponseWhich;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.MultipleRecords;
import org.folio.rest.support.builders.CheckInBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class CheckInStorageApiTest extends ApiTests {
  private static final String TABLE_NAME = "check_in";

  private final AssertingRecordClient checkInClient =
    new AssertingRecordClient(client, StorageTestSuite.TENANT_ID,
      InterfaceUrls::checkInsStorageUrl, "checkIns");

  @BeforeEach
  void beforeEach() {
    StorageTestSuite.cleanUpTable(TABLE_NAME);
  }

  @AfterEach
  void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs(TABLE_NAME);
  }

  @Test
  void canCreateCheckIn() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInToCreate = createSampleCheckIn().create();

    IndividualResource createResult = checkInClient.create(checkInToCreate);

    assertThat(createResult.getJson(), is(checkInToCreate));

    assertCreateEventForCheckIn(checkInToCreate);
  }

  @Test
  void canCreateRecordWithoutId() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInToCreate = createSampleCheckIn()
      .withId(null)
      .create();

    IndividualResource createResult = checkInClient.create(checkInToCreate);

    assertThat(createResult.getId(), notNullValue());
    assertThat(createResult.getJson(), hasSameProperties(checkInToCreate));
    assertCreateEventForCheckIn(checkInToCreate);
  }

  @Test
  void cannotCreateCheckInIfRequiredPropertyMissing() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    UUID recordId = UUID.randomUUID();
    JsonObject checkInToCreate = new CheckInBuilder().withId(recordId).create();

    JsonResponse createResponse = checkInClient.attemptCreate(checkInToCreate);

    assertThat(createResponse, isValidationResponseWhich(hasMessage("must not be null")));
    assertThat(createResponse, isValidationResponseWhich(hasParameter("occurredDateTime", "null")));
    assertThat(checkInClient.attemptGetById(recordId).getStatusCode(), is(404));

    assertNoCheckInEvent(checkInToCreate.getString("id"));
  }

  @Test
  void canFilterCheckInsByProperties()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    final UUID servicePointId = UUID.randomUUID();
    final UUID nodItemId = UUID.randomUUID();
    final UUID bobUserId = UUID.randomUUID();

    JsonObject itemCheckInRecord = createSampleCheckIn()
      .withItemId(nodItemId)
      .create();
    JsonObject servicePointCheckInRecord = createSampleCheckIn()
      .withServicePointId(servicePointId)
      .create();
    JsonObject userCheckInRecord = createSampleCheckIn()
      .withPerformedByUserId(bobUserId)
      .create();

    final DateTime occurredDateTime = DateTime.now(DateTimeZone.UTC).plusHours(1);
    JsonObject occurredDateTimeCheckInRecord = createSampleCheckIn()
      .withOccurredDateTime(occurredDateTime)
      .create();

    checkInClient.create(itemCheckInRecord);
    checkInClient.create(servicePointCheckInRecord);
    checkInClient.create(userCheckInRecord);
    checkInClient.create(occurredDateTimeCheckInRecord);

    MultipleRecords<JsonObject> itemCheckInSearch = checkInClient
      .getMany(String.format("itemId == %s", nodItemId));
    assertThat(itemCheckInSearch.getTotalRecords(), is(1));
    assertTrue(itemCheckInSearch.getRecords().contains(itemCheckInRecord));

    MultipleRecords<JsonObject> servicePointCheckInSearch = checkInClient
      .getMany(String.format("servicePointId == %s", servicePointId));
    assertThat(servicePointCheckInSearch.getTotalRecords(), is(1));
    assertTrue(servicePointCheckInSearch.getRecords().contains(servicePointCheckInRecord));

    MultipleRecords<JsonObject> userCheckInSearch = checkInClient
      .getMany(String.format("performedByUserId == %s", bobUserId));
    assertThat(userCheckInSearch.getTotalRecords(), is(1));
    assertTrue(userCheckInSearch.getRecords().contains(userCheckInRecord));

    MultipleRecords<JsonObject> occurredDateTimeCheckInSearch = checkInClient
      .getMany(String.format("occurredDateTime = '%s'", occurredDateTime
        // Use RMB format to match
        .toString("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")));
    assertThat(occurredDateTimeCheckInSearch.getTotalRecords(), is(1));
    assertTrue(occurredDateTimeCheckInSearch.getRecords().contains(occurredDateTimeCheckInRecord));
  }

  @Test
  void cannotUseNegativeOffsetForSearch()
    throws InterruptedException, ExecutionException, TimeoutException,
    MalformedURLException {

    JsonResponse jsonResponse = checkInClient
      .attemptGetMany("occurredDateTime > 10", -10, 10);

    assertThat(jsonResponse.getStatusCode(), is(400));
    assertThat(jsonResponse.getBody().trim(),
      matchesPattern("'offset' .+ must be greater than or equal to 0"));
  }

  @Test
  void canGetCheckInById() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInToCreate = createSampleCheckIn().create();

    IndividualResource createResult = checkInClient.create(checkInToCreate);
    IndividualResource checkInById = checkInClient.getById(createResult.getId());

    assertThat(createResult.getJson(), is(checkInToCreate));
    assertThat(checkInById.getJson(), is(checkInToCreate));
  }

  @Test
  void cannotGetCheckInByIdIfDoesNotExists() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonResponse checkInById = checkInClient.attemptGetById(UUID.randomUUID());

    assertThat(checkInById.getStatusCode(), is(404));
  }

  @Test
  void canCreateRecordWithoutOptionalParameters() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    JsonObject checkInToCreate = createSampleCheckIn()
      .withItemStatusPriorToCheckIn(null)
      .withItemLocationId(null)
      .withRequestQueueSize(null)
      .create();

    IndividualResource createResult = checkInClient.create(checkInToCreate);

    assertThat(createResult.getJson(), is(checkInToCreate));

    assertCreateEventForCheckIn(checkInToCreate);
  }

  @Test
  void cannotCreateRecordWithNegativeRequestQueueSize() throws InterruptedException,
    ExecutionException, TimeoutException, MalformedURLException {

    UUID recordId = UUID.randomUUID();
    JsonObject checkInToCreate = createSampleCheckIn()
      .withId(recordId)
      .withRequestQueueSize(-1)
      .create();

    JsonResponse createResult = checkInClient.attemptCreate(checkInToCreate);

    assertThat(createResult,
      isValidationResponseWhich(hasMessage("must be greater than or equal to 0")));
    assertThat(createResult,
      isValidationResponseWhich(hasParameter("requestQueueSize", "-1")));
    assertThat(checkInClient.attemptGetById(recordId).getStatusCode(), is(404));

    assertNoCheckInEvent(checkInToCreate.getString("id"));
  }

  private CheckInBuilder createSampleCheckIn() {
    return new CheckInBuilder()
      .withId(UUID.randomUUID())
      .withOccurredDateTime(DateTime.now(DateTimeZone.UTC))
      .withItemId(UUID.randomUUID())
      .withServicePointId(UUID.randomUUID())
      .withPerformedByUserId(UUID.randomUUID())
      .withItemStatusPriorToCheckIn("Available")
      .withItemLocationId(UUID.randomUUID())
      .withRequestQueueSize(0);
  }
}
