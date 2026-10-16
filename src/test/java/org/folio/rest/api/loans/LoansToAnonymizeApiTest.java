package org.folio.rest.api.loans;

import static java.lang.String.format;
import static org.folio.rest.support.http.InterfaceUrls.loansToAnonymizeUrl;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isBadRequest;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isOk;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.LoanRequestBuilder;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import lombok.SneakyThrows;

public class LoansToAnonymizeApiTest extends ApiTests {
  private static final DateTime NOW = DateTime.now(DateTimeZone.UTC);
  private static final DateTime LIBRARY_POLICY_CUTOFF = NOW.minusDays(30);
  private static final DateTime AS_EARLY_AS_POSSIBLE_CUTOFF = NOW.minusDays(7);

  private final AssertingRecordClient loansClient = new AssertingRecordClient(
    client, StorageTestSuite.TENANT_ID, InterfaceUrls::loanStorageUrl, "loans");

  @BeforeEach
  void beforeEach() {
    truncateTables("loan, user_loan_anonymization_preference");
  }

  @Test
  void returnsClosedLoansWithUserWithoutDateFilter() {
    String closedLoan = createClosedLoan(randomUserId(), NOW);
    createLoan(new LoanRequestBuilder().open().withUserId(UUID.randomUUID()));
    createLoan(new LoanRequestBuilder().closed().withNoUserId());

    assertThat(getLoansToAnonymize(""), containsInAnyOrder(closedLoan));
  }

  @Test
  void excludesLoansOfNeverUsers() {
    String neverUser = randomUserId();
    storePreference(neverUser, "never");
    createClosedLoan(neverUser, NOW.minusYears(5));
    createClosedLoan(neverUser, null);

    assertThat(getLoansToAnonymize(""), empty());
    assertThat(getLoansToAnonymize(allCutoffs()), empty());
  }

  @Test
  void filtersLoansOfLibraryPolicyUsersByLibraryPolicyCutoff() {
    String user = randomUserId();
    String due = createClosedLoan(user, LIBRARY_POLICY_CUTOFF.minusMinutes(1));
    createClosedLoan(user, LIBRARY_POLICY_CUTOFF.plusMinutes(1));

    assertThat(getLoansToAnonymize(allCutoffs()), containsInAnyOrder(due));
  }

  @Test
  void filtersLoansOfAsEarlyAsPossibleUsersByAsEarlyAsPossibleCutoff() {
    String earlyUser = randomUserId();
    String libraryUser = randomUserId();
    storePreference(earlyUser, "asEarlyAsPossible");
    DateTime betweenCutoffs = LIBRARY_POLICY_CUTOFF.plusDays(1);
    String earlyUsersLoan = createClosedLoan(earlyUser, betweenCutoffs);
    createClosedLoan(libraryUser, betweenCutoffs);
    createClosedLoan(earlyUser, AS_EARLY_AS_POSSIBLE_CUTOFF.plusMinutes(1));

    assertThat(getLoansToAnonymize(allCutoffs()), containsInAnyOrder(earlyUsersLoan));
  }

  @Test
  void asEarlyAsPossibleCutoffDoesNotApplyToLibraryPolicyUsers() {
    String earlyUser = randomUserId();
    storePreference(earlyUser, "asEarlyAsPossible");
    String earlyUsersLoan = createClosedLoan(earlyUser, NOW);
    String libraryUsersLoan = createClosedLoan(randomUserId(), NOW);

    assertThat(getLoansToAnonymize("?libraryPolicyReturnedBefore=" + encode(LIBRARY_POLICY_CUTOFF)),
      containsInAnyOrder(earlyUsersLoan));
    assertThat(getLoansToAnonymize("?asEarlyAsPossibleReturnedBefore="
      + encode(AS_EARLY_AS_POSSIBLE_CUTOFF)), containsInAnyOrder(libraryUsersLoan));
  }

  @Test
  void excludeLibraryPolicyKeepsLoansOfAsEarlyAsPossibleUsers() {
    String earlyUser = randomUserId();
    storePreference(earlyUser, "asEarlyAsPossible");
    String earlyUsersLoan = createClosedLoan(earlyUser, NOW.minusYears(1));
    createClosedLoan(randomUserId(), NOW.minusYears(1));

    assertThat(getLoansToAnonymize("?excludeLibraryPolicy=true"),
      containsInAnyOrder(earlyUsersLoan));
  }

  @Test
  void excludeLibraryPolicyStillExcludesNeverUsers() {
    String neverUser = randomUserId();
    storePreference(neverUser, "never");
    createClosedLoan(neverUser, NOW.minusYears(1));

    assertThat(getLoansToAnonymize("?excludeLibraryPolicy=true"), empty());
  }

  @Test
  void loansWithoutSystemReturnDatePassTheDateFilter() {
    String earlyUser = randomUserId();
    storePreference(earlyUser, "asEarlyAsPossible");
    String libraryUsersLoan = createClosedLoan(randomUserId(), null);
    String earlyUsersLoan = createClosedLoan(earlyUser, null);

    assertThat(getLoansToAnonymize(allCutoffs()),
      containsInAnyOrder(libraryUsersLoan, earlyUsersLoan));
  }

  @ParameterizedTest
  @ValueSource(strings = { "libraryPolicy", "unknownValue" })
  void otherPreferenceValuesFollowTheLibraryPolicy(String preference) {
    String user = randomUserId();
    storePreference(user, preference);
    String due = createClosedLoan(user, LIBRARY_POLICY_CUTOFF.minusMinutes(1));
    createClosedLoan(user, AS_EARLY_AS_POSSIBLE_CUTOFF.minusMinutes(1));

    assertThat(getLoansToAnonymize(allCutoffs()), containsInAnyOrder(due));
  }

  @Test
  void loanWithMalformedUserIdDoesNotBreakTheQuery() {
    String malformedUsersLoan = createLoan(new LoanRequestBuilder().closed()
      .withSystemReturnDate(NOW.minusYears(1)), "not-a-uuid");

    assertThat(getLoansToAnonymize(allCutoffs()), containsInAnyOrder(malformedUsersLoan));
  }

  @Test
  void userIdIsMatchedCaseInsensitively() {
    String neverUser = randomUserId();
    storePreference(neverUser, "never");
    createLoan(new LoanRequestBuilder().closed()
      .withUserId(UUID.fromString(neverUser))
      .withSystemReturnDate(NOW.minusYears(1)), neverUser.toUpperCase());

    assertThat(getLoansToAnonymize(""), empty());
  }

  @Test
  void returnsAtMostLimitLoans() {
    for (int i = 0; i < 3; i++) {
      createClosedLoan(randomUserId(), NOW);
    }

    assertThat(getLoansToAnonymize("?limit=0"), empty());
    assertThat(getLoansToAnonymize("?limit=2"), hasSize(2));
    assertThat(getLoansToAnonymize("?limit=50000"), hasSize(3));
  }

  @Test
  void malformedCutoffIsRejected() {
    JsonResponse response = get("?libraryPolicyReturnedBefore=2026-13-01");

    assertThat(response, isBadRequest());
    assertThat(response.getBody(), containsString("libraryPolicyReturnedBefore"));
  }

  private static String allCutoffs() {
    return "?libraryPolicyReturnedBefore=" + encode(LIBRARY_POLICY_CUTOFF)
      + "&asEarlyAsPossibleReturnedBefore=" + encode(AS_EARLY_AS_POSSIBLE_CUTOFF);
  }

  private static String encode(DateTime dateTime) {
    return URLEncoder.encode(dateTime.toString(), StandardCharsets.UTF_8);
  }

  private static String randomUserId() {
    return UUID.randomUUID().toString();
  }

  /**
   * @param systemReturnDate null creates a closed loan without system return date
   */
  @SneakyThrows
  private String createClosedLoan(String userId, DateTime systemReturnDate) {
    JsonObject loan = new LoanRequestBuilder().closed()
      .withUserId(UUID.fromString(userId))
      .withSystemReturnDate(systemReturnDate)
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

  private static void storePreference(String userId, String preference) {
    waitFor(pgClient.execute(
      format("INSERT INTO %s.user_loan_anonymization_preference (id, jsonb) VALUES ($1, $2)",
        pgClient.getSchemaName()),
      Tuple.of(UUID.fromString(userId),
        new JsonObject().put("id", userId).put("preference", preference))));
  }

  private List<String> getLoansToAnonymize(String queryString) {
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
    client.get(loansToAnonymizeUrl(queryString), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));
    return getCompleted.get(5, TimeUnit.SECONDS);
  }
}
