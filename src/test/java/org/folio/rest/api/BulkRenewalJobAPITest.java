package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.MultipleRecords;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

class BulkRenewalJobAPITest extends ApiTests {
  private static final String TABLE_NAME = "bulk_renewal_job";
  private static final String COLLECTION_PROPERTY = "bulkRenewalJobs";
  private static final String STARTED_DATE = "2026-01-01T10:00:00.000Z";

  private final AssertingRecordClient bulkRenewalJobClient =
    new AssertingRecordClient(client, StorageTestSuite.TENANT_ID,
      InterfaceUrls::bulkRenewalJobsUrl, COLLECTION_PROPERTY);

  @BeforeEach
  void beforeEach() {
    StorageTestSuite.cleanUpTable(TABLE_NAME);
  }

  @Test
  @SneakyThrows
  void canCreateAndRetrieveJob() {
    JsonObject job = validJob();
    String id = job.getString("id");

    JsonObject created = bulkRenewalJobClient.create(job).getJson();
    JsonObject retrieved = bulkRenewalJobClient.getById(id).getJson();

    assertThat(created.getString("id"), is(id));
    assertThat(retrieved.getString("id"), is(id));
    assertThat(retrieved.getString("status"), is("RUNNING"));
    assertThat(OffsetDateTime.parse(retrieved.getString("startedDate")).toInstant(),
      is(Instant.parse(STARTED_DATE)));
    assertThat(retrieved.getInteger("totalEligibleLoans"), is(0));
    assertThat(retrieved.getInteger("processedLoans"), is(0));
    assertThat(retrieved.getInteger("successfulRenewals"), is(0));
    assertThat(retrieved.getInteger("failedRenewals"), is(0));
    assertThat(retrieved.getJsonObject("metadata"), is(notNullValue()));
  }

  @Test
  @SneakyThrows
  void canDeleteJob() {
    UUID id = UUID.randomUUID();
    bulkRenewalJobClient.create(validJob(id.toString()));

    assertThat(bulkRenewalJobClient.attemptDeleteById(id).getStatusCode(), is(204));
    assertThat(bulkRenewalJobClient.attemptGetById(id).getStatusCode(), is(404));
  }

  @Test
  @SneakyThrows
  void unknownIdReturnsNotFound() {
    assertThat(bulkRenewalJobClient.attemptGetById(UUID.randomUUID()).getStatusCode(), is(404));
  }

  @Test
  @SneakyThrows
  void duplicateIdIsRejected() {
    JsonObject job = validJob();
    bulkRenewalJobClient.create(job);

    assertThat(bulkRenewalJobClient.attemptCreate(job).getStatusCode(), is(422));
    assertThat(bulkRenewalJobClient.getAll().getTotalRecords(), is(1));
  }

  @Test
  @SneakyThrows
  void updateAdvancesVersion() {
    JsonObject created = bulkRenewalJobClient.create(validJob()).getJson();
    String id = created.getString("id");
    JsonObject stored = bulkRenewalJobClient.getById(id).getJson();
    int version = stored.getInteger("_version");

    JsonObject update = stored.copy()
      .put("status", "COMPLETED")
      .put("endedDate", "2026-01-01T11:00:00.000Z")
      .put("processedLoans", 5)
      .put("reason", "done");

    assertThat(bulkRenewalJobClient.attemptPutById(update).getStatusCode(), is(204));

    JsonObject updated = bulkRenewalJobClient.getById(id).getJson();
    assertThat(updated.getString("status"), is("COMPLETED"));
    assertThat(updated.getInteger("processedLoans"), is(5));
    assertThat(updated.getString("reason"), is("done"));
    assertThat(updated.getString("endedDate"), is(notNullValue()));
    assertThat(updated.getInteger("_version"), is(version + 1));
  }

  @Test
  @SneakyThrows
  void staleVersionIsRejected() {
    String id = bulkRenewalJobClient.create(validJob()).getId().toString();
    JsonObject stored = bulkRenewalJobClient.getById(id).getJson();

    JsonObject firstUpdate = stored.copy().put("processedLoans", 3);
    JsonObject secondUpdate = stored.copy().put("processedLoans", 7);

    assertThat(bulkRenewalJobClient.attemptPutById(firstUpdate).getStatusCode(), is(204));
    assertThat(bulkRenewalJobClient.attemptPutById(secondUpdate).getStatusCode(), is(409));

    assertThat(bulkRenewalJobClient.getById(id).getJson().getInteger("processedLoans"), is(3));
  }

  @Test
  @SneakyThrows
  void putWithoutVersionOnExistingRecordDoesNotOverwrite() {
    String id = bulkRenewalJobClient.create(validJob()).getId().toString();
    JsonObject stored = bulkRenewalJobClient.getById(id).getJson();

    JsonObject withoutVersion = stored.copy().put("processedLoans", 9);
    withoutVersion.remove("_version");

    int status = bulkRenewalJobClient.attemptPutById(withoutVersion).getStatusCode();

    assertThat(status, is(409));
    assertThat(bulkRenewalJobClient.getById(id).getJson().getInteger("processedLoans"), is(0));
  }

  @Test
  @SneakyThrows
  void clientSuppliedVersionOnCreateIsNotTrusted() {
    JsonObject job = validJob().put("_version", 99);

    String id = bulkRenewalJobClient.create(job).getId().toString();

    assertThat(bulkRenewalJobClient.getById(id).getJson().getInteger("_version"), is(1));
  }

  @Test
  @SneakyThrows
  void canQueryByStatus() {
    seedJobs();

    MultipleRecords<JsonObject> running = bulkRenewalJobClient.getMany("status==RUNNING");

    assertThat(running.getTotalRecords(), is(2));
    assertThat(statuses(running), everyItem(is("RUNNING")));
  }

  @Test
  @SneakyThrows
  void canQueryByStartedDateRange() {
    seedJobs();

    MultipleRecords<JsonObject> recent =
      bulkRenewalJobClient.getMany("startedDate>=2026-01-03T00:00:00.000Z");

    assertThat(recent.getTotalRecords(), is(3));
  }

  @Test
  @SneakyThrows
  void canSortAndPage() {
    seedJobs();

    MultipleRecords<JsonObject> page = bulkRenewalJobClient.getMany(
      "cql.allRecords=1 sortBy startedDate/sort.descending", 1, 2);

    assertThat(page.getTotalRecords(), is(5));
    assertThat(page.getRecords().size(), is(2));
    assertThat(startedInstants(page), contains(
      Instant.parse("2026-01-04T10:00:00.000Z"), Instant.parse("2026-01-03T10:00:00.000Z")));
  }

  @Test
  @SneakyThrows
  void invalidCqlIsRejected() {
    int status = bulkRenewalJobClient.attemptGetMany("status=", null, null).getStatusCode();

    assertThat(status, is(400));
  }

  @Test
  @SneakyThrows
  void zeroCountsAreAccepted() {
    JsonObject created = bulkRenewalJobClient.create(validJob()).getJson();

    assertThat(created.getInteger("failedRenewals"), is(0));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidJobs")
  @SneakyThrows
  void invalidJobIsRejectedOnCreate(String description, int expectedStatus,
    UnaryOperator<JsonObject> breakJob) {
    JsonObject invalid = breakJob.apply(validJob());

    assertThat(bulkRenewalJobClient.attemptCreate(invalid).getStatusCode(), is(expectedStatus));
    assertThat(bulkRenewalJobClient.getAll().getTotalRecords(), is(0));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidJobs")
  @SneakyThrows
  void invalidJobIsRejectedOnUpdate(String description, int expectedStatus,
    UnaryOperator<JsonObject> breakJob) {
    String id = bulkRenewalJobClient.create(validJob()).getId().toString();
    JsonObject stored = bulkRenewalJobClient.getById(id).getJson();

    int status = bulkRenewalJobClient.attemptPutById(breakJob.apply(stored.copy()))
      .getStatusCode();

    assertThat(status, is(expectedStatus));
    JsonObject afterAttempt = bulkRenewalJobClient.getById(id).getJson();
    assertThat(afterAttempt.getString("status"), is("RUNNING"));
    assertThat(afterAttempt.getInteger("_version"), is(stored.getInteger("_version")));
  }

  // RMB answers 422 for constraint violations (missing, negative, unknown property) and 400
  // when Jackson cannot parse a value (unsupported enum constant, malformed date-time).
  private static Stream<Arguments> invalidJobs() {
    return Stream.of(
      invalid("missing status", 422, job -> job.remove("status")),
      invalid("missing startedDate", 422, job -> job.remove("startedDate")),
      invalid("missing totalEligibleLoans", 422, job -> job.remove("totalEligibleLoans")),
      invalid("missing failedRenewals", 422, job -> job.remove("failedRenewals")),
      invalid("unsupported status", 400, job -> job.put("status", "PAUSED")),
      invalid("invalid startedDate", 400, job -> job.put("startedDate", "yesterday")),
      invalid("invalid endedDate", 400, job -> job.put("endedDate", "not-a-date")),
      invalid("negative statistic", 422, job -> job.put("failedRenewals", -1)),
      invalid("unknown property", 422, job -> job.put("unexpected", "value")));
  }

  private static Arguments invalid(String description, int expectedStatus,
    Consumer<JsonObject> breakJob) {

    UnaryOperator<JsonObject> operator = job -> {
      breakJob.accept(job);
      return job;
    };
    return Arguments.of(description, expectedStatus, operator);
  }

  @SneakyThrows
  private void seedJobs() {
    createJob("RUNNING", "2026-01-01T10:00:00.000Z");
    createJob("RUNNING", "2026-01-02T10:00:00.000Z");
    createJob("STOPPING", "2026-01-03T10:00:00.000Z");
    createJob("COMPLETED", "2026-01-04T10:00:00.000Z");
    createJob("FAILED", "2026-01-05T10:00:00.000Z");
  }

  @SneakyThrows
  private void createJob(String status, String startedDate) {
    bulkRenewalJobClient.create(validJob().put("status", status).put("startedDate", startedDate));
  }

  private static List<String> statuses(MultipleRecords<JsonObject> records) {
    return records.getRecords().stream().map(job -> job.getString("status")).toList();
  }

  private static List<Instant> startedInstants(MultipleRecords<JsonObject> records) {
    return records.getRecords().stream()
      .map(job -> OffsetDateTime.parse(job.getString("startedDate")).toInstant())
      .toList();
  }

  private static JsonObject validJob() {
    return validJob(UUID.randomUUID().toString());
  }

  private static JsonObject validJob(String id) {
    return new JsonObject()
      .put("id", id)
      .put("status", "RUNNING")
      .put("startedDate", STARTED_DATE)
      .put("totalEligibleLoans", 0)
      .put("processedLoans", 0)
      .put("successfulRenewals", 0)
      .put("failedRenewals", 0);
  }
}
