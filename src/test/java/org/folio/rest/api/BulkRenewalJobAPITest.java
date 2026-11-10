package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
