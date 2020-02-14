package org.folio.rest.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import static org.folio.rest.impl.CirculationRulesAPI.CIRCULATION_RULES_TABLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LoanNotice;
import org.folio.rest.jaxrs.model.PatronNoticePolicies;
import org.folio.rest.jaxrs.model.PatronNoticePolicy;
import org.folio.rest.jaxrs.model.SendOptions;
import org.folio.rest.jaxrs.resource.FixedDueDateScheduleStorage;
import org.folio.rest.jaxrs.resource.PatronNoticePolicyStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.support.exception.NoticePolicyInUseException;

public class PatronNoticePoliciesAPI implements PatronNoticePolicyStorage {

  private static final Logger logger = LoggerFactory.getLogger(PatronNoticePoliciesAPI.class);

  public static final String PATRON_NOTICE_POLICY_TABLE = "patron_notice_policy";
  public static final String STATUS_CODE_DUPLICATE_NAME = "duplicate.name";
  public static final String NOT_FOUND = "Not found";
  private static final String INTERNAL_SERVER_ERROR = "Internal server error";
  private static final String IN_USE_POLICY_ERROR_MESSAGE = "Cannot delete in use notice policy";
  private static final String VALIDATION_ERROR_MESSAGE = "This option is not valid for selected";

  @Override
  public void getPatronNoticePolicyStoragePatronNoticePolicies(
    int offset,
    int limit,
    String query,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
        PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);

        String[] fieldList = {"*"};

        CQL2PgJSON cql2pgJson = new CQL2PgJSON(PATRON_NOTICE_POLICY_TABLE + ".jsonb");
        CQLWrapper cql = new CQLWrapper(cql2pgJson, query)
          .setLimit(new Limit(limit))
          .setOffset(new Offset(offset));

        pgClient.get(PATRON_NOTICE_POLICY_TABLE, PatronNoticePolicy.class, fieldList, cql, true, get -> {
          if (get.failed()) {
            logger.error(get.cause());
            asyncResultHandler.handle(succeededFuture(
              GetPatronNoticePolicyStoragePatronNoticePoliciesResponse.respond500WithTextPlain(get.cause())));
            return;
          }

          PatronNoticePolicies patronNoticePolicies = new PatronNoticePolicies()
            .withPatronNoticePolicies(get.result().getResults())
            .withTotalRecords(get.result().getResultInfo().getTotalRecords());

          asyncResultHandler.handle(succeededFuture(
            GetPatronNoticePolicyStoragePatronNoticePoliciesResponse
              .respond200WithApplicationJson(patronNoticePolicies)));

        });
      } catch (Exception e) {
        logger.error(e);
        asyncResultHandler.handle(succeededFuture(
          GetPatronNoticePolicyStoragePatronNoticePoliciesResponse.respond500WithTextPlain(e)));
      }
    });
  }

  @Override
  public void postPatronNoticePolicyStoragePatronNoticePolicies(
    PatronNoticePolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
        PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);

        if (entity.getId() == null) {
          entity.setId(UUID.randomUUID().toString());
        }

        pgClient.save(PATRON_NOTICE_POLICY_TABLE, entity.getId(), entity, save -> {
          if (save.failed()) {
            logger.error(save.cause());
            if (ValidationHelper.isDuplicate(save.cause().getMessage())) {
              asyncResultHandler.handle(succeededFuture(
                PostPatronNoticePolicyStoragePatronNoticePoliciesResponse
                  .respond422WithApplicationJson(createNotUniqueNameErrors(entity.getName()))));
            }
            asyncResultHandler.handle(succeededFuture(
              PostPatronNoticePolicyStoragePatronNoticePoliciesResponse.respond500WithTextPlain(save.cause())));
            return;
          }
          asyncResultHandler.handle(succeededFuture(
            PostPatronNoticePolicyStoragePatronNoticePoliciesResponse.respond201WithApplicationJson(entity)));
        });
      } catch (Exception e) {
        logger.error(e);
        asyncResultHandler.handle(succeededFuture(
          PostPatronNoticePolicyStoragePatronNoticePoliciesResponse.respond500WithTextPlain(e)));
      }
    });
  }

  @Override
  public void getPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyId(
    String patronNoticePolicyId,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(PATRON_NOTICE_POLICY_TABLE, PatronNoticePolicy.class, patronNoticePolicyId, okapiHeaders,
      vertxContext, GetPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse.class,
      asyncResultHandler);
  }

  @Override
  public void deletePatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyId(
    String patronNoticePolicyId,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PostgresClient pgClient = PgUtil.postgresClient(vertxContext, okapiHeaders);

    findCirculationRules(pgClient)
      .map(CirculationRules::getRulesAsText)
      .map(text -> text.contains(patronNoticePolicyId))
      .compose(contains -> contains ? failedFuture(new NoticePolicyInUseException(IN_USE_POLICY_ERROR_MESSAGE)) : succeededFuture())
      .compose(v -> deleteNoticePolicyById(pgClient, patronNoticePolicyId))
      .map(v -> DeletePatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse.respond204())
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .setHandler(asyncResultHandler);
  }

  @Override
  public void putPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyId(
    String patronNoticePolicyId,
    PatronNoticePolicy entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    Errors errors = validateEntity(entity);
    if (!errors.getErrors().isEmpty()) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        FixedDueDateScheduleStorage.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
          .respond422WithApplicationJson(errors)));
      return;
    }

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
        PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);

        pgClient.update(PATRON_NOTICE_POLICY_TABLE, entity, patronNoticePolicyId, update -> {
          if (update.failed()) {
            logger.error(update.cause());
            if (ValidationHelper.isDuplicate(update.cause().getMessage())) {
              asyncResultHandler.handle(succeededFuture(
                PutPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse
                  .respond422WithApplicationJson(createNotUniqueNameErrors(entity.getName()))));
              return;
            }
            asyncResultHandler.handle(succeededFuture(
              PutPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse
                .respond500WithTextPlain(update.cause())));
            return;
          }

          if (update.result().getUpdated() == 0) {
            asyncResultHandler.handle(succeededFuture(
              PutPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse
                .respond404WithTextPlain(NOT_FOUND)));
            return;
          }
          asyncResultHandler.handle(succeededFuture(
            PutPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse.respond204()));
        });
      } catch (Exception e) {
        logger.error(e);
        asyncResultHandler.handle(succeededFuture(
          PutPatronNoticePolicyStoragePatronNoticePoliciesByPatronNoticePolicyIdResponse.respond500WithTextPlain(e)));
      }
    });
  }

  private Errors createNotUniqueNameErrors(String name) {
    Error error = new Error();
    error.setMessage(format("'%s' name in not unique", name));
    error.setCode(STATUS_CODE_DUPLICATE_NAME);
    return new Errors().withErrors(singletonList(error));
  }

  private Future<CirculationRules> findCirculationRules(PostgresClient pgClient) {

    Promise<Results<CirculationRules>> promise = Promise.promise();
    pgClient.get(CIRCULATION_RULES_TABLE, CirculationRules.class, new Criterion(), false, promise.future());
    return promise.future().map(Results::getResults)
      .compose(list -> list.size() == 1 ? succeededFuture(list) :
        failedFuture(new IllegalStateException("Number of records in circulation_rules table is " + list.size())))
      .map(list -> list.get(0));
  }

  private Future<Void> deleteNoticePolicyById(PostgresClient pgClient, String id) {

    Promise<UpdateResult> promise = Promise.promise();
    pgClient.delete(PATRON_NOTICE_POLICY_TABLE, id, promise.future());

    return promise.future().map(UpdateResult::getUpdated)
      .compose(updated -> updated > 0 ? succeededFuture() : failedFuture(new NotFoundException(NOT_FOUND)));
  }

  private Response mapExceptionToResponse(Throwable t) {

    if (t.getClass() == NotFoundException.class) {
      return Response.status(404)
        .header(CONTENT_TYPE, TEXT_PLAIN)
        .entity(t.getMessage())
        .build();
    }

    if (t.getClass() == NoticePolicyInUseException.class) {
      return Response.status(400)
        .header(CONTENT_TYPE, TEXT_PLAIN)
        .entity(t.getMessage())
        .build();
    }

    return Response.status(500)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(INTERNAL_SERVER_ERROR)
      .build();
  }

  private Errors validateEntity(PatronNoticePolicy entity) {
    List<Error> loanNoticesErrors = entity.getLoanNotices().stream()
      .map(this::validateLoanNotice)
      .map(Errors::getErrors)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
    return new Errors().withErrors(loanNoticesErrors);
  }

  private Errors validateLoanNotice(LoanNotice loanNotice) {
    List<Errors> errors = new ArrayList<>();
    SendOptions sendOptions = loanNotice.getSendOptions();
    if (sendOptions.getSendWhen() != SendOptions.SendWhen.DUE_DATE) {
      errors.add(validateSendOptionsForNotDueDate(sendOptions));
    } else {
      errors.add(validateFrequencyForDueDate(loanNotice));
    }
    errors.add(validateOneTimeFrequency(loanNotice));

    List<Error> mergedErrors = errors.stream()
      .map(Errors::getErrors)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    return new Errors().withErrors(mergedErrors);
  }

  private Errors validateFrequencyForDueDate(LoanNotice loanNotice) {
    SendOptions sendOptions = loanNotice.getSendOptions();
    if (sendOptions.getSendHow() != SendOptions.SendHow.BEFORE
      && sendOptions.getSendHow() != SendOptions.SendHow.AFTER && loanNotice.getFrequency() != null) {

      return ValidationHelper.createValidationErrorMessage("frequency",
        loanNotice.getFrequency().toString(), VALIDATION_ERROR_MESSAGE + " Send");
    }
    return new Errors().withErrors(Collections.emptyList());
  }

  private Errors validateOneTimeFrequency(LoanNotice loanNotice) {
    if (loanNotice.getFrequency() != null && loanNotice.getFrequency() == LoanNotice.Frequency.ONE_TIME
      && loanNotice.getSendOptions().getSendEvery() != null) {
      return ValidationHelper.createValidationErrorMessage("sendEvery",
        loanNotice.getSendOptions().getSendEvery().toString(), VALIDATION_ERROR_MESSAGE + " Frequency");
    }
    return new Errors().withErrors(Collections.emptyList());
  }

  private Errors validateSendOptionsForNotDueDate(SendOptions sendOptions) {
    if (sendOptions.getSendHow() != null) {
       return ValidationHelper.createValidationErrorMessage("sendHow",
        sendOptions.getSendHow().toString(), VALIDATION_ERROR_MESSAGE + " Triggering event");
    }
    if (sendOptions.getSendBy() != null) {
      return ValidationHelper.createValidationErrorMessage("sendBy",
        sendOptions.getSendBy().toString(), VALIDATION_ERROR_MESSAGE + " Triggering event");
    }
    if (sendOptions.getSendEvery() != null) {
      return ValidationHelper.createValidationErrorMessage("sendEvery",
        sendOptions.getSendEvery().toString(), VALIDATION_ERROR_MESSAGE + " Triggering event");
    }
    return new Errors().withErrors(Collections.emptyList());
  }
}
