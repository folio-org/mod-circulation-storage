package org.folio.rest.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import static org.folio.rest.impl.CirculationRulesAPI.CIRCULATION_RULES_TABLE;
import static org.folio.rest.persist.Criteria.Criteria.OP_EQUAL;

import java.util.Map;
import java.util.UUID;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;

import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.PatronNoticePolicies;
import org.folio.rest.jaxrs.model.PatronNoticePolicy;
import org.folio.rest.jaxrs.resource.PatronNoticePolicyStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
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

        pgClient.get(PATRON_NOTICE_POLICY_TABLE, PatronNoticePolicy.class, fieldList, cql, true, false, get -> {
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

    Future<Results<CirculationRules>> future = Future.future();
    pgClient.get(CIRCULATION_RULES_TABLE, CirculationRules.class, new Criterion(), false, false, future.completer());
    return future.map(Results::getResults)
      .compose(list -> list.size() == 1 ? succeededFuture(list) :
        failedFuture(new IllegalStateException("Number of records in circulation_rules table is " + list.size())))
      .map(list -> list.get(0));
  }

  private Future<Void> deleteNoticePolicyById(PostgresClient pgClient, String id) {

    Criteria criteria = new Criteria()
      .addField("'id'")
      .setOperation(OP_EQUAL)
      .setValue("'" + id + "'");

    Future<UpdateResult> future = Future.future();
    pgClient.delete(PATRON_NOTICE_POLICY_TABLE, new Criterion(criteria), future.completer());

    return future.map(UpdateResult::getUpdated)
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
}
