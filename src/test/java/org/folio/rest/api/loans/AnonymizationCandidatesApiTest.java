package org.folio.rest.api.loans;

import static java.lang.String.format;
import static org.folio.rest.support.http.InterfaceUrls.anonymizationCandidatesUrl;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isBadRequest;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isOk;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.jaxrs.model.LoanStorageAnonymizationCandidatesGetPreference;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.service.loan.LoanService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import lombok.SneakyThrows;

public class AnonymizationCandidatesApiTest extends ApiTests {
  private static final String EARLY = "asEarlyAsPossible";
  private static final String LIBRARY = "libraryPolicy";
  private static final DateTime NOW = DateTime.now(DateTimeZone.UTC).withMillisOfSecond(0);
  private static final DateTime CUTOFF = NOW.minusDays(30);

  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::loanStorageUrl, "loans");

  @BeforeEach
  void beforeEach() {
    truncateTables("loan, user_loan_anonymization_preference");
  }

  @Test
  void eachGroupOnlyReturnsItsOwnUsers() {
    String earlyUser = storedUser(EARLY);
    String neverUser = storedUser("never");
    String earlyUsersLoan = createClosedLoan(earlyUser, NOW);
    String libraryUsersLoan = createClosedLoan(randomUserId(), NOW);
    createClosedLoan(neverUser, NOW.minusYears(5));

    assertThat(getAnonymizationCandidates(EARLY, null), containsInAnyOrder(earlyUsersLoan));
    assertThat(getAnonymizationCandidates(LIBRARY, null), containsInAnyOrder(libraryUsersLoan));
  }

  @ParameterizedTest
  @ValueSource(strings = { EARLY, LIBRARY })
  void openAndAnonymizedLoansAreNeverReturned(String preference) {
    String user = userOfGroup(preference);
    String closedLoan = createClosedLoan(user, NOW);
    createLoan(new LoanRequestBuilder().open().withUserId(UUID.fromString(user)));
    createLoan(new LoanRequestBuilder().closed().withNoUserId());

    assertThat(getAnonymizationCandidates(preference, null), containsInAnyOrder(closedLoan));
  }

  @ParameterizedTest
  @ValueSource(strings = { LIBRARY, "unknownValue" })
  void explicitLibraryPolicyAndUnknownValuesBelongToLibraryPolicy(String storedPreference) {
    String loan = createClosedLoan(storedUser(storedPreference), NOW);

    assertThat(getAnonymizationCandidates(LIBRARY, null), containsInAnyOrder(loan));
    assertThat(getAnonymizationCandidates(EARLY, null), empty());
  }

  @ParameterizedTest
  @ValueSource(strings = { EARLY, LIBRARY })
  void returnedBeforeIsStrict(String preference) {
    String user = userOfGroup(preference);
    String before = createClosedLoan(user, CUTOFF.minusSeconds(1));
    createClosedLoan(user, CUTOFF);
    createClosedLoan(user, CUTOFF.plusSeconds(1));

    assertThat(getAnonymizationCandidates(preference, CUTOFF), containsInAnyOrder(before));
  }

  @ParameterizedTest
  @ValueSource(strings = { EARLY, LIBRARY })
  void loanWithoutSystemReturnDateOnlyPassesWithoutDate(String preference) {
    String loan = createClosedLoan(userOfGroup(preference), null);

    assertThat(getAnonymizationCandidates(preference, null), containsInAnyOrder(loan));
    assertThat(getAnonymizationCandidates(preference, CUTOFF), empty());
  }

  @Test
  void uppercaseUserIdIsMatched() {
    String earlyUser = storedUser(EARLY);
    String neverUser = storedUser("never");
    String earlyLoan = createLoan(closedLoanBuilder(NOW), earlyUser.toUpperCase());
    createLoan(closedLoanBuilder(NOW), neverUser.toUpperCase());

    assertThat(getAnonymizationCandidates(EARLY, null), containsInAnyOrder(earlyLoan));
    assertThat(getAnonymizationCandidates(LIBRARY, null), empty());
  }

  @Test
  void invalidUserIdDoesNotBreakTheQuery() {
    String loan = createLoan(closedLoanBuilder(NOW), "not-a-uuid");

    assertThat(getAnonymizationCandidates(LIBRARY, null), containsInAnyOrder(loan));
    assertThat(getAnonymizationCandidates(EARLY, null), empty());
  }

  @ParameterizedTest
  @ValueSource(strings = { "Z", "+02:00" })
  void returnedBeforeAcceptsOffsetFormats(String offset) {
    String user = randomUserId();
    String before = createClosedLoan(user, CUTOFF.minusSeconds(1));
    createClosedLoan(user, CUTOFF.plusSeconds(1));

    ZoneOffset zoneOffset = "Z".equals(offset) ? ZoneOffset.UTC : ZoneOffset.ofHours(2);
    String formatted = OffsetDateTime.parse(CUTOFF.toString())
      .withOffsetSameInstant(zoneOffset)
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")) + offset;

    assertThat(getAnonymizationCandidates("?preference=" + LIBRARY + "&limit=10&returnedBefore="
      + encode(formatted)), containsInAnyOrder(before));
  }

  @Test
  void returnsAtMostLimitLoans() {
    for (int i = 0; i < 3; i++) {
      createClosedLoan(randomUserId(), NOW);
    }

    assertThat(getAnonymizationCandidates("?preference=" + LIBRARY + "&limit=2"), hasSize(2));
    assertThat(getAnonymizationCandidates("?preference=" + LIBRARY + "&limit=50000"), hasSize(3));
    assertThat(getAnonymizationCandidates("?preference=" + LIBRARY), hasSize(3));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "?preference=libraryPolicy&limit=10&returnedBefore=2026-13-01T00:00:00Z",
    "?preference=libraryPolicy&limit=10&returnedBefore=2026-02-30T00:00:00Z",
    "?preference=libraryPolicy&limit=10&returnedBefore=2026-01-01T00:00:00",
    "?preference=libraryPolicy&limit=10&returnedBefore=2026-01-01T00:00:00%2B0200",
    "?preference=libraryPolicy&limit=10&returnedBefore=2026-01-01",
    "?preference=libraryPolicy&limit=0",
    "?preference=libraryPolicy&limit=50001",
    "?preference=never&limit=10",
    "?limit=10",
  })
  void invalidRequestIsRejected(String queryString) {
    assertThat(get(queryString), isBadRequest());
  }

  @Test
  void malformedReturnedBeforeIsNamedInTheError() {
    JsonResponse response = get("?preference=libraryPolicy&limit=10&returnedBefore=2026-13-01");

    assertThat(response, isBadRequest());
    assertThat(response.getBody(), containsString("returnedBefore"));
  }

  @Test
  void asEarlyAsPossibleQueryUsesTheUserIdIndex() {
    String sql = LoanService.anonymizationCandidatesSql(pgClient.getSchemaName(),
      LoanStorageAnonymizationCandidatesGetPreference.ASEARLYASPOSSIBLE);
    Tuple params = Tuple.tuple().addOffsetDateTime(null).addInteger(10);

    // Few test rows: without this, a sequential scan is always cheaper
    var planRows = waitFor(pgClient.withTrans(conn -> conn.execute("SET LOCAL enable_seqscan = off")
      .compose(x -> conn.execute("EXPLAIN " + sql, params))));
    var plan = new StringBuilder();
    planRows.forEach(row -> plan.append(row.getString(0)).append('\n'));

    assertThat(plan.toString(), containsString("loan_userid_idx"));
    assertThat(plan.toString(), not(containsString("Seq Scan on loan ")));
  }

  private String userOfGroup(String preference) {
    return EARLY.equals(preference) ? storedUser(EARLY) : randomUserId();
  }

  private static String storedUser(String preference) {
    String userId = randomUserId();
    waitFor(pgClient.execute(
      format("INSERT INTO %s.user_loan_anonymization_preference (id, jsonb) VALUES ($1, $2)",
        pgClient.getSchemaName()),
      Tuple.of(UUID.fromString(userId),
        new JsonObject().put("id", userId).put("preference", preference))));
    return userId;
  }

  private static String randomUserId() {
    return UUID.randomUUID().toString();
  }

  private static LoanRequestBuilder closedLoanBuilder(DateTime systemReturnDate) {
    return new LoanRequestBuilder().closed().withSystemReturnDate(systemReturnDate);
  }

  /** systemReturnDate null: loan without systemReturnDate */
  @SneakyThrows
  private String createClosedLoan(String userId, DateTime systemReturnDate) {
    JsonObject loan = closedLoanBuilder(systemReturnDate)
      .withUserId(UUID.fromString(userId))
      .create();
    if (systemReturnDate == null) {
      loan.remove("systemReturnDate");
    }
    return loansClient.create(loan).getId();
  }

  @SneakyThrows
  private String createLoan(LoanRequestBuilder builder) {
    return loansClient.create(builder).getId();
  }

  @SneakyThrows
  private String createLoan(LoanRequestBuilder builder, String userId) {
    return loansClient.create(builder.create().put("userId", userId)).getId();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private List<String> getAnonymizationCandidates(String preference, DateTime returnedBefore) {
    String queryString = "?preference=" + preference + "&limit=50000";
    if (returnedBefore != null) {
      queryString += "&returnedBefore=" + encode(returnedBefore.toString());
    }
    return getAnonymizationCandidates(queryString);
  }

  private List<String> getAnonymizationCandidates(String queryString) {
    JsonResponse response = get(queryString);
    assertThat(response, isOk());

    JsonObject body = response.getJson();
    List<String> ids = body.getJsonArray("loans").stream()
      .map(JsonObject.class::cast)
      .map(loan -> loan.getString("id"))
      .toList();
    assertThat(body.getInteger("totalRecords"), is(ids.size()));
    return ids;
  }

  @SneakyThrows
  private JsonResponse get(String queryString) {
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(anonymizationCandidatesUrl(queryString), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));
    return getCompleted.get(5, TimeUnit.SECONDS);
  }
}
