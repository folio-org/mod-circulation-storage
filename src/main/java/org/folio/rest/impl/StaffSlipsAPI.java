package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.folio.rest.jaxrs.model.StaffSlip;
import org.folio.rest.jaxrs.model.StaffSlips;
import org.folio.rest.jaxrs.resource.LoanStorage;
import org.folio.rest.jaxrs.resource.StaffSlipsStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.StringJoiner;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class StaffSlipsAPI implements StaffSlipsStorage {

  private static final String STAFF_SLIP_TABLE = "staff_slips";

  private static final Class<StaffSlip> STAFF_SLIP_CLASS = StaffSlip.class;

  @Override
  public void deleteStaffSlipsStorageStaffSlips(String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {

      try {

        PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(
          // TODO: Need to add 204 response to staff slips RAML interface definition!
          String.format("TRUNCATE TABLE %s_%s.%s", tenantId, "mod_circulation_storage", STAFF_SLIP_TABLE),
          reply -> asyncResultHandler.handle(succeededFuture(
            DeleteStaffSlipsStorageStaffSlipsResponse.noContent().build())));

      } catch (Exception e) {
        asyncResultHandler
          .handle(succeededFuture(
            StaffSlipsStorage.DeleteStaffSlipsStorageStaffSlipsResponse
              .respond500WithTextPlain(e.getMessage())));
      }
    });

  }

  @Override
  public void getStaffSlipsStorageStaffSlips(int offset, int limit, String query, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.get(STAFF_SLIP_TABLE, STAFF_SLIP_CLASS, StaffSlips.class, query, offset, limit, okapiHeaders, vertxContext,
        GetStaffSlipsStorageStaffSlipsResponse.class, asyncResultHandler);
  }

  @Override
  public void postStaffSlipsStorageStaffSlips(String lang, StaffSlip entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    ImmutablePair<Boolean, String> validationResult = validateStaffSlip(entity);

    if (!validationResult.getLeft()) {
      asyncResultHandler.handle(succeededFuture(StaffSlipsStorage.PostStaffSlipsStorageStaffSlipsResponse
        .respond400WithTextPlain(validationResult.getRight())));

      return;
    }

    createStaffSlip(entity, okapiHeaders, asyncResultHandler, vertxContext);

  }

  @Override
  public void getStaffSlipsStorageStaffSlipsByStaffSlipId(String staffSlipId, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(STAFF_SLIP_TABLE, STAFF_SLIP_CLASS, staffSlipId, okapiHeaders, vertxContext,
        GetStaffSlipsStorageStaffSlipsByStaffSlipIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteStaffSlipsStorageStaffSlipsByStaffSlipId(String staffSlipId, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.deleteById(STAFF_SLIP_TABLE, staffSlipId, okapiHeaders, vertxContext,
        DeleteStaffSlipsStorageStaffSlipsByStaffSlipIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putStaffSlipsStorageStaffSlipsByStaffSlipId(String staffSlipId, String lang, StaffSlip entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    ImmutablePair<Boolean, String> validationResult = validateStaffSlip(entity);

    if (!validationResult.getLeft()) {
      asyncResultHandler.handle(succeededFuture(
        LoanStorage.PostLoanStorageLoansResponse.respond400WithTextPlain(validationResult.getRight())));

      return;
    }

    PgUtil.put(STAFF_SLIP_TABLE, entity, staffSlipId, okapiHeaders, vertxContext,
        PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse.class, reply -> {
          if (reply.failed() || reply.result().getStatus() != 404) {
            asyncResultHandler.handle(reply);
            return;
          }
          entity.setId(staffSlipId);

          // FIXME: This returns the non-declared 201 status code by using
          // PostStaffSlipsStorageStaffSlipsResponse
          createStaffSlip(entity, okapiHeaders, asyncResultHandler, vertxContext);
        });
  }

  private void createStaffSlip(StaffSlip entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (entity.getActive() == null) {
      entity.setActive(true);
    }
    PgUtil.post(STAFF_SLIP_TABLE, entity, okapiHeaders, vertxContext,
        PostStaffSlipsStorageStaffSlipsResponse.class, asyncResultHandler);
  }

  private ImmutablePair<Boolean, String> validateStaffSlip(StaffSlip staffSlip) {

    Boolean valid = true;
    StringJoiner messages = new StringJoiner("\n");

    if (staffSlip.getName() == null || staffSlip.getName().equals("")) {
      valid = false;
      messages.add("Name is a required property");
    }

    if (staffSlip.getTemplate() == null || staffSlip.getTemplate().equals("")) {
      valid = false;
      messages.add("Template is a required property");
    }

    return new ImmutablePair<>(valid, messages.toString());
  }

}
