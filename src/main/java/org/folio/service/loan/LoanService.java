package org.folio.service.loan;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.service.event.EntityChangedEventPublisherFactory.loanEventPublisher;
import static org.folio.support.LoanAnonymizationPreference.AS_EARLY_AS_POSSIBLE;
import static org.folio.support.LoanAnonymizationPreference.NEVER;
import static org.folio.support.ModuleConstants.LOAN_CLASS;
import static org.folio.support.ModuleConstants.LOAN_HISTORY_TABLE;
import static org.folio.support.ModuleConstants.LOAN_TABLE;
import static org.folio.support.ModuleConstants.MODULE_NAME;
import static org.folio.support.ModuleConstants.OPEN_LOAN_STATUS;
import static org.folio.support.ModuleConstants.USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.persist.LoanRepository;
import org.folio.rest.impl.util.OkapiResponseUtil;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.LoanStorageLoansToAnonymizeGetPreference;
import org.folio.rest.jaxrs.model.Loans;
import org.folio.rest.jaxrs.model.LoansHistoryItem;
import org.folio.rest.jaxrs.model.LoansHistoryItems;
import org.folio.rest.jaxrs.model.Status;
import org.folio.rest.jaxrs.resource.LoanStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.service.event.EntityChangedEventPublisher;
import org.folio.support.CqlQuery;
import org.folio.support.ResponseUtil;
import org.folio.support.ResultHandlerFactory;
import org.folio.support.ServerErrorResponder;
import org.folio.support.ServiceHelper;
import org.folio.support.UUIDValidation;
import org.folio.support.VertxContextRunner;
import org.joda.time.DateTime;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Tuple;
import jakarta.validation.constraints.NotNull;

public class LoanService {

  private static final Logger log = LogManager.getLogger(LoanService.class);
  private static final ObjectMapper MAPPER = ObjectMapperTool.getMapper();
  // ISO 8601 date-time with offset Z, +HH:MM or +HHMM
  private static final DateTimeFormatter RETURNED_BEFORE_FORMAT = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
    .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
    .toFormatter()
    .withResolverStyle(ResolverStyle.STRICT);  // rejects 2026-02-30

  /**
   * Without $1: no date filter. With $1: only loans with a readable systemReturnDate before $1.
   * The CASE makes sure invalid dates (e.g. "", 2021-02-30) are skipped, not cast.
   */
  private static final String RETURNED_BEFORE_FILTER = """
    ($1::timestamptz IS NULL OR
        CASE WHEN pg_input_is_valid(loan.jsonb->>'systemReturnDate', 'timestamptz')
          THEN (loan.jsonb->>'systemReturnDate')::timestamptz < $1::timestamptz
          ELSE false
        END)""";

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final LoanRepository repository;
  private final EntityChangedEventPublisher<String, Loan> eventPublisher;
  private final ServiceHelper<Loan> helper;

  public LoanService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    this.postgresClient = postgresClient(vertxContext, okapiHeaders);
    this.repository = new LoanRepository(vertxContext, okapiHeaders);
    this.eventPublisher = loanEventPublisher(vertxContext, okapiHeaders);
    this.helper = new ServiceHelper<>(repository, eventPublisher);
  }

  public Future<Response> findByQuery(String query, int offset, int limit) {
    return PgUtil.get(LOAN_TABLE, LOAN_CLASS, Loans.class, query, offset, limit, okapiHeaders, vertxContext,
        LoanStorage.GetLoanStorageLoansResponse.class);
  }

  public Future<Response> findById(String loanId) {
    return PgUtil.getById(LOAN_TABLE, LOAN_CLASS, loanId, okapiHeaders, vertxContext,
        LoanStorage.GetLoanStorageLoansByLoanIdResponse.class);
  }

  public Future<Response> create(Loan loan) {
    log.info("create:: Creating loan: itemId: {}", loan.getItemId());
    if (loan.getStatus() == null) {
      loan.setStatus(new Status().withName(OPEN_LOAN_STATUS));
    }
    log.info("create:: Status is not null");
    if (isOpenAndHasNoUserId(loan)) {
      return respondWithError(
        LoanStorage.PostLoanStorageLoansResponse::respond422WithApplicationJson,
        "Open loan must have a user ID");
    }
    log.info("create:: isOpenAndHasNoUserId() validation passed");
    //TODO: Convert this to use validation responses (422 and error of errors)
    ImmutablePair<Boolean, String> validationResult = validateLoan(loan);
    log.info("create:: validateLoan() validation executed");
    if (!validationResult.getLeft()) {
      return succeededFuture(LoanStorage.PostLoanStorageLoansResponse
        .respond400WithTextPlain(validationResult.getRight()));
    }
    log.info("create:: validateLoan() validation passed");
    Promise<Response> createResult = Promise.promise();
    log.info("create:: Executing PgUtil.post...");
    PgUtil.post(LOAN_TABLE, loan, okapiHeaders, vertxContext,
      LoanStorage.PostLoanStorageLoansResponse.class, reply -> {
        log.info("create:: PgUtil.post returned, succeeded={}", reply.succeeded());
        if (isMultipleOpenLoanError(reply)) {
          log.info("create:: isMultipleOpenLoanError is true");
          createResult.complete(LoanStorage.PostLoanStorageLoansResponse
            .respond422WithApplicationJson(moreThanOneOpenLoanError(loan)));
        } else {
          log.info("create:: isMultipleOpenLoanError is false, handling and returning");
          createResult.handle(reply);
        }
      });

    log.info("create:: Returning result...");
    return createResult.future()
      .map(r -> {
        log.info("create:: Composing eventPublisher.publishCreated() to PgUtil.post result");
        return r;
      })
      .compose(eventPublisher.publishCreated());
  }

  public Future<Response> createOrUpdate(String loanId, Loan loan) {
    if (loan.getStatus() == null) {
      loan.setStatus(new Status().withName(OPEN_LOAN_STATUS));
    }

    ImmutablePair<Boolean, String> validationResult = validateLoan(loan);

    if (!validationResult.getLeft()) {
      return succeededFuture(LoanStorage.PutLoanStorageLoansByLoanIdResponse
                  .respond400WithTextPlain(validationResult.getRight()));
    }

    if (isOpenAndHasNoUserId(loan)) {
      return respondWithError(
          LoanStorage.PutLoanStorageLoansByLoanIdResponse::respond422WithApplicationJson,
          "Open loan must have a user ID");
    }

    return helper.upsertAndPublishEvents(loanId, loan)
        .map(checkForMultipleOpenLoanError(loan))
        .otherwise(err -> {
          log.error("Failed to store loan: id = {}", loanId, err);

          return ResponseUtil.internalErrorResponse(err);
        });
  }

  public Future<Response> deleteById(String loanId) {
    return repository.getById(loanId)
        .compose(loan -> {
          final Promise<Response> deleteResult = promise();

          PgUtil.deleteById(LOAN_TABLE, loanId, okapiHeaders, vertxContext,
              LoanStorage.DeleteLoanStorageLoansByLoanIdResponse.class, deleteResult::handle);

          return deleteResult.future()
              .compose(eventPublisher.publishRemoved(loan));
        });
  }
  public Future<Response> deleteByCql(String cql) {
    if (isBlank(cql)) {
      return succeededFuture(LoanStorage.DeleteLoanStorageLoansResponse.respond400WithTextPlain(
          "Expected CQL but query parameter is empty"));
    }
    if (new CqlQuery(cql).isMatchingAll()) {
      return deleteAll();  // faster: TRUNCATE (not DELETE), only one Kafka message
    }

    return repository.deleteByCql(cql)
      .map(rowSet -> {
        rowSet.iterator().forEachRemaining(row -> {
          var loanId = row.getString(0);
          try {
            var loan = MAPPER.readValue(row.getString(1), Loan.class);
            eventPublisher.publishRemoved(loanId, loan);
          } catch (IOException e) {
            log.error("deleteByCql:: Failed to parse json of loanId {}: {}", loanId, e.getMessage(), e);
          }
        });
        return Response.noContent().build();
      })
      .otherwise(e -> {
        log.error("deleteByCql:: {}", e.getMessage(), e);
        if (e instanceof CQLQueryValidationException) {
          return LoanStorage.DeleteLoanStorageLoansResponse.respond400WithTextPlain(e.getMessage());
        }
        return LoanStorage.DeleteLoanStorageLoansResponse.respond500WithTextPlain("Internal server error, see log");
      });
  }

  private Future<Response> deleteAll() {
    return postgresClient.execute(String.format("TRUNCATE TABLE %s_%s.loan", tenantId(okapiHeaders), MODULE_NAME))
        .<Response>map(LoanStorage.DeleteLoanStorageLoansResponse.respond204())
        .otherwise(e -> LoanStorage.DeleteLoanStorageLoansResponse.respond500WithTextPlain(e.getMessage()))
        .compose(eventPublisher.publishAllRemoved());
  }

  public Future<Response> getLoanHistory(String query, int offset, int limit) {
    String cql = query;
    if (StringUtils.isBlank(cql)) {
      cql = "cql.allRecords=1";
    }
    if (!cql.toLowerCase().contains(" sortby ")) {
      cql += " sortBy createdDate/sort.descending";
    }

    return PgUtil.get(LOAN_HISTORY_TABLE, LoansHistoryItem.class, LoansHistoryItems.class, cql, offset, limit,
        okapiHeaders, vertxContext, LoanStorage.GetLoanStorageLoanHistoryResponse.class);
  }

  /**
   * Loans of one preference group for the scheduled anonymization.
   * A prefilter: must never be stricter than the checkers in mod-circulation.
   */
  public Future<Response> findLoansToAnonymize(LoanStorageLoansToAnonymizeGetPreference preference,
    String returnedBefore, int limit) {

    // RMB checks limit (@Min/@Max); a missing or unknown enum value arrives as null
    if (preference == null) {
      return succeededFuture(LoanStorage.GetLoanStorageLoansToAnonymizeResponse
        .respond400WithTextPlain("preference must be asEarlyAsPossible or libraryPolicy"));
    }

    final OffsetDateTime cutoff;
    try {
      cutoff = parseReturnedBefore(returnedBefore);
    } catch (IllegalArgumentException e) {
      return succeededFuture(LoanStorage.GetLoanStorageLoansToAnonymizeResponse
        .respond400WithTextPlain(e.getMessage()));
    }

    Tuple params = Tuple.tuple().addOffsetDateTime(cutoff).addInteger(limit)
      .addString(AS_EARLY_AS_POSSIBLE.value());
    if (preference == LoanStorageLoansToAnonymizeGetPreference.LIBRARYPOLICY) {
      params.addString(NEVER.value());
    }

    return postgresClient.execute(loansToAnonymizeSql(postgresClient.getSchemaName(), preference),
        params)
      .map(rows -> Response.ok(rows.iterator().next().getString(0), APPLICATION_JSON).build())
      .otherwise(e -> {
        log.error("findLoansToAnonymize:: {}", e.getMessage(), e);
        return LoanStorage.GetLoanStorageLoansToAnonymizeResponse
          .respond500WithTextPlain("Internal server error, see log");
      });
  }

  private static OffsetDateTime parseReturnedBefore(String value) {
    if (isBlank(value)) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value, RETURNED_BEFORE_FORMAT);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("returnedBefore is not a valid date-time: " + value, e);
    }
  }

  /**
   * $1 returnedBefore, $2 limit, $3 "asEarlyAsPossible", $4 "never".
   * Builds the JSON in SQL, so a malformed loan field can't fail the request.
   * No ORDER BY, as before: rejected loans can take the same slots on every run.
   */
  public static String loansToAnonymizeSql(String schemaName,
    LoanStorageLoansToAnonymizeGetPreference preference) {

    return """
      SELECT jsonb_build_object(
        'loans', COALESCE(jsonb_agg(selected.jsonb), '[]'::jsonb),
        'totalRecords', count(*))::text
      FROM (%s) selected
      """.formatted(selectLoansToAnonymizeSql(schemaName, preference));
  }

  private static String selectLoansToAnonymizeSql(String schemaName,
    LoanStorageLoansToAnonymizeGetPreference preference) {

    // Index lookup per user: must match the userId index of the loan table exactly.
    // LATERAL with LIMIT stops the planner from scanning the whole loan table, it misjudges the jsonb filters.
    if (preference == LoanStorageLoansToAnonymizeGetPreference.ASEARLYASPOSSIBLE) {
      return """
        SELECT loan.jsonb
        FROM %1$s.%3$s preference
        CROSS JOIN LATERAL (
          SELECT loan.jsonb
          FROM %1$s.%2$s loan
          WHERE left(lower(%1$s.f_unaccent(loan.jsonb->>'userId')), 600) = preference.id::text
            AND loan.jsonb->'status'->>'name' = 'Closed'
            AND %4$s
          LIMIT $2) loan
        WHERE preference.jsonb->>'preference' = $3
        LIMIT $2
        """.formatted(schemaName, LOAN_TABLE, USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE,
          RETURNED_BEFORE_FILTER);
    }

    // Text comparison, so a malformed userId can't break the query
    return """
      SELECT loan.jsonb
      FROM %1$s.%2$s loan
      WHERE loan.jsonb->'status'->>'name' = 'Closed'
        AND loan.jsonb->>'userId' IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM %1$s.%3$s preference
          WHERE preference.id::text = lower(loan.jsonb->>'userId')
            AND preference.jsonb->>'preference' IN ($3, $4))
        AND %4$s
      LIMIT $2
      """.formatted(schemaName, LOAN_TABLE, USER_LOAN_ANONYMIZATION_PREFERENCE_TABLE,
        RETURNED_BEFORE_FILTER);
  }

  public Future<Response> anonymizeByUserId(String userId) {
    Promise<Response> promise = Promise.promise();

    final ServerErrorResponder serverErrorResponder =
        new ServerErrorResponder(LoanStorage.PostLoanStorageLoansAnonymizeByUserIdResponse
            ::respond500WithTextPlain, promise::handle, log);

    final VertxContextRunner runner = new VertxContextRunner(
        vertxContext, serverErrorResponder::withError);

    runner.runOnContext(() -> {
      if (!UUIDValidation.isValidUUID(userId)) {
        final Errors errors = ValidationHelper.createValidationErrorMessage(
            "userId", userId, "Invalid user ID, should be a UUID");

        promise.complete(LoanStorage.PostLoanStorageLoansAnonymizeByUserIdResponse
                .respond422WithApplicationJson(errors));
        return;
      }

      final String combinedAnonymizationSql = createAnonymizationSQL(userId, tenantId(okapiHeaders));

      postgresClient.execute(combinedAnonymizationSql, ResultHandlerFactory.when(
          s -> promise.complete(LoanStorage.PostLoanStorageLoansAnonymizeByUserIdResponse.respond204()),
          serverErrorResponder::withError));
    });

    return promise.future();
  }

  private boolean isOpenAndHasNoUserId(Loan loan) {
    return Objects.equals(loan.getStatus().getName(), OPEN_LOAN_STATUS)
        && loan.getUserId() == null;
  }

  private ImmutablePair<Boolean, String> validateLoan(Loan loan) {
    boolean valid = true;
    StringJoiner messages = new StringJoiner("\n");

    //ISO8601 is less strict than RFC3339 so will not catch some issues
    try {
      DateTime.parse(loan.getLoanDate());
    } catch (Exception e) {
      valid = false;
      messages.add("loan date must be a date time (in RFC3339 format)");
    }

    if (loan.getReturnDate() != null) {
      //ISO8601 is less strict than RFC3339 so will not catch some issues
      try {
        DateTime.parse(loan.getReturnDate());
      } catch (Exception e) {
        valid = false;
        messages.add("return date must be a date time (in RFC3339 format)");
      }
    }

    return new ImmutablePair<>(valid, messages.toString());
  }

  private boolean isMultipleOpenLoanError(AsyncResult<Response> reply) {
    return OkapiResponseUtil.containsErrorMessage(
        reply, "value already exists in table loan: ");
  }

  private Function<Response, Response> checkForMultipleOpenLoanError(Loan loan) {
    return response -> isMultipleOpenLoanErrorOnUpsert(response)
        ? LoanStorage.PutLoanStorageLoansByLoanIdResponse.respond422WithApplicationJson(moreThanOneOpenLoanError(loan))
        : response;
  }

  // Remove/Replace this function when MyPgUtil.putUpsert204() is removed/replaced.
  private boolean isMultipleOpenLoanErrorOnUpsert(Response response) {
    return response.getStatus() == HTTP_BAD_REQUEST.toInt()
        && response.hasEntity()
        && response.getEntity().toString().contains("loan_itemid_idx_unique");
  }

  private Errors moreThanOneOpenLoanError(Loan entity) {
    return ValidationHelper.createValidationErrorMessage(
        "itemId", entity.getItemId(),
        "Cannot have more than one open loan for the same item");
  }

  private Future<Response> respondWithError(Function<Errors, Response> responseCreator, String message) {
    final ArrayList<Error> errorsList = new ArrayList<>();

    errorsList.add(new Error().withMessage(message));

    final Errors errors = new Errors().withErrors(errorsList);

    return respondWithErrors(responseCreator, errors);
  }

  private Future<Response> respondWithErrors(Function<Errors, Response> responseCreator, Errors errors) {
    return succeededFuture(responseCreator.apply(errors));
  }

  private String createAnonymizationSQL(
      @NotNull String userId,
      String tenantId) {

    final String anonymizeLoansSql = String.format(
        "UPDATE %s_%s.loan"
            + " SET jsonb = jsonb - 'userId'"
            + " WHERE jsonb->>'userId' = '" + userId + "'"
            + " AND jsonb->'status'->>'name' = 'Closed'",
        tenantId, MODULE_NAME);

    //Only anonymize the history for loans that are currently closed
    //meaning that we need to refer to loans in this query
    final String anonymizeLoansActionHistorySql = String.format(
        "UPDATE %s_%s.%s"
            + " SET jsonb = jsonb #- '{loan,userId}'"
            + " WHERE jsonb->'loan'->>'id' IN"
            + "   (SELECT l.jsonb->>'id'"
            + "    FROM %s_%s.loan l"
            + "    WHERE l.jsonb->>'userId' = '" + userId + "'"
            + "      AND l.jsonb->'status'->>'name' = 'Closed')",
        tenantId, MODULE_NAME, LOAN_HISTORY_TABLE,
        tenantId, MODULE_NAME);

    //Loan action history needs to go first, as needs to be for specific loans
    return anonymizeLoansActionHistorySql + "; " + anonymizeLoansSql;
  }

}
