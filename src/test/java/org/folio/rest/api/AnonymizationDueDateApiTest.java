package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Exercises the {@code loan_anonymization_due} SQL against Postgres: the stamp
 * upsert, the clears, the due/unevaluated finders, and the strip removing both
 * userId and the due-date row.
 *
 * <p>The tenant carries FOLIO sample loans, so assertions track the specific
 * test loan ({@code hasItem}) rather than the whole finder listing.</p>
 */
class AnonymizationDueDateApiTest extends ApiTests {
  private static final String PAST = "2020-01-01T00:00:00.000Z";
  private static final String FUTURE = "2099-01-01T00:00:00.000Z";

  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, TENANT_ID, InterfaceUrls::loanStorageUrl, "loans");

  private final String loanId = UUID.randomUUID().toString();

  @BeforeEach
  void beforeEach() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    StorageTestSuite.deleteAll(InterfaceUrls.loanStorageUrl());
    closedLoanWithUser(loanId);
  }

  @Test
  void unevaluatedListsClosedLoanWithNoRowYet() throws Exception {
    assertThat(unevaluatedIds(), hasItem(loanId));
    assertThat(dueIds(), not(hasItem(loanId)));
  }

  @Test
  void stampPastDueAppearsInDueAndLeavesUnevaluated() throws Exception {
    assertThat(stamp(entry(loanId, PAST)).getJson().getInteger("updated"), is(1));

    assertThat(dueIds(), hasItem(loanId));
    assertThat(unevaluatedIds(), not(hasItem(loanId)));
  }

  @Test
  void stampFutureIsEvaluatedButNotYetDue() throws Exception {
    stamp(entry(loanId, FUTURE));

    assertThat(dueIds(), not(hasItem(loanId)));
    assertThat(unevaluatedIds(), not(hasItem(loanId)));
  }

  @Test
  void stampNeverIsEvaluatedAndNeverDue() throws Exception {
    assertThat(stamp(entry(loanId, null)).getJson().getInteger("updated"), is(1));

    assertThat(dueIds(), not(hasItem(loanId)));
    assertThat(unevaluatedIds(), not(hasItem(loanId)));
  }

  @Test
  void stampSkipsOpenLoans() throws Exception {
    final String openId = UUID.randomUUID().toString();
    loansClient.create(new LoanRequestBuilder().withId(UUID.fromString(openId))
      .withItemId(UUID.randomUUID()).withUserId(UUID.randomUUID()).open().create());

    assertThat(stamp(entry(openId, PAST)).getJson().getInteger("updated"), is(0));
    assertThat(dueIds(), not(hasItem(openId)));
  }

  @Test
  void stampRejectsMalformedDueDate() throws Exception {
    assertThat(stamp(entry(loanId, "not-a-date")).getStatusCode(), is(422));
  }

  /**
   * mod-circulation formats due_at with {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}
   * (AnonymizationDueDateStorageRepository#formatDueAt); this endpoint must
   * accept exactly that.
   */
  @Test
  void stampAcceptsTheCanonicalWireFormatFromModCirculation() throws Exception {
    for (String dueAt : List.of("2020-01-01T00:00:00.000Z", "2099-12-31T23:59:59.999Z",
      "2021-05-15T08:15:43.123Z")) {

      assertThat("storage must accept mod-circulation's format: " + dueAt,
        stamp(entry(loanId, dueAt)).getStatusCode(), is(200));
    }
  }

  @Test
  void clearByLoanIdsReturnsToUnevaluated() throws Exception {
    stamp(entry(loanId, PAST));
    assertThat(unevaluatedIds(), not(hasItem(loanId)));

    final JsonResponse cleared = clear(new JsonObject()
      .put("loanIds", new JsonArray(List.of(loanId))));
    assertThat(cleared.getStatusCode(), is(200));
    assertThat(cleared.getJson().getInteger("updated"), is(1));
    assertThat(unevaluatedIds(), hasItem(loanId));
  }

  @Test
  void clearAllReturnsEveryLoanToUnevaluated() throws Exception {
    final String otherId = UUID.randomUUID().toString();
    closedLoanWithUser(otherId);
    stamp(entry(loanId, PAST), entry(otherId, FUTURE));
    assertThat(unevaluatedIds(), not(hasItem(loanId)));
    assertThat(unevaluatedIds(), not(hasItem(otherId)));

    assertThat(clear(new JsonObject().put("all", true)).getStatusCode(), is(200));
    assertThat(unevaluatedIds(), hasItem(loanId));
    assertThat(unevaluatedIds(), hasItem(otherId));
  }

  @Test
  void clearRequiresExactlyOneSelector() throws Exception {
    assertThat(clear(new JsonObject()).getStatusCode(), is(422));
    assertThat(clear(new JsonObject()
      .put("loanIds", new JsonArray(List.of(loanId)))
      .put("all", true)).getStatusCode(), is(422));
  }

  @Test
  void stripRemovesUserIdAndSideRow() throws Exception {
    stamp(entry(loanId, PAST));
    assertThat(dueIds(), hasItem(loanId));

    final CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.post(InterfaceUrls.anonymizeLoansURL(),
      new JsonObject().put("loanIds", new JsonArray(List.of(loanId))), TENANT_ID, json(completed));
    assertThat(get(completed).getStatusCode(), is(200));

    assertThat(loansClient.getById(loanId).getJson().getString("userId"), is(nullValue()));
    // No userId -> neither finder can return it, and the row is gone.
    assertThat(dueIds(), not(hasItem(loanId)));
    assertThat(unevaluatedIds(), not(hasItem(loanId)));
  }

  // ---- helpers ----

  private void closedLoanWithUser(String id) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    loansClient.create(new LoanRequestBuilder()
      .withId(UUID.fromString(id))
      .withItemId(UUID.randomUUID())
      .withUserId(UUID.randomUUID())
      .closed()
      .create());
  }

  private static JsonObject entry(String loanId, String dueAt) {
    final JsonObject e = new JsonObject().put("loanId", loanId);
    if (dueAt != null) {
      e.put("dueAt", dueAt);
    }
    return e;
  }

  private JsonResponse stamp(JsonObject... entries) throws MalformedURLException {
    final JsonObject body = new JsonObject().put("entries", new JsonArray(List.of(entries)));
    final CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.post(InterfaceUrls.anonymizationDueDateStampURL(), body, TENANT_ID, json(completed));
    return get(completed);
  }

  private JsonResponse clear(JsonObject body) throws MalformedURLException {
    final CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.post(InterfaceUrls.anonymizationDueDateClearURL(), body, TENANT_ID, json(completed));
    return get(completed);
  }

  private List<String> dueIds() throws MalformedURLException {
    return loanIds(InterfaceUrls.anonymizationDueDateDueURL("?limit=1000"));
  }

  private List<String> unevaluatedIds() throws MalformedURLException {
    return loanIds(InterfaceUrls.anonymizationDueDateUnevaluatedURL("?limit=1000"));
  }

  private List<String> loanIds(java.net.URL url) {
    final CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    client.get(url, TENANT_ID, json(completed));
    final JsonArray loans = get(completed).getJson().getJsonArray("loans", new JsonArray());
    final List<String> ids = new ArrayList<>();
    loans.forEach(o -> ids.add(((JsonObject) o).getString("id")));
    return ids;
  }
}
