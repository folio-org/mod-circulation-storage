package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.folio.rest.jaxrs.model.StaffSlip;
import org.folio.rest.jaxrs.model.StaffSlips;
import org.folio.rest.jaxrs.resource.LoanStorageResource;
import org.folio.rest.jaxrs.resource.StaffSlipsStorageResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.Headers.TENANT_HEADER;

public class StaffSlipsAPI implements StaffSlipsStorageResource {

	private static final String STAFF_SLIP_TABLE = "staff_slips";

	private static final Class<StaffSlip> STAFF_SLIP_CLASS = StaffSlip.class;

	private static final Logger log = LoggerFactory.getLogger(STAFF_SLIP_CLASS);

	@Override
	public void deleteStaffSlipsStorageStaffSlips(String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

		String tenantId = okapiHeaders.get(TENANT_HEADER);

		vertxContext.runOnContext(v -> {

			try {

				PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
						TenantTool.calculateTenantId(tenantId));

				postgresClient.mutate(
				  //TODO: Need to add 204 response to staff slips RAML interface definition!
          String.format("TRUNCATE TABLE %s_%s.%s", tenantId, "mod_circulation_storage", STAFF_SLIP_TABLE), reply ->
            asyncResultHandler.handle(succeededFuture(
              DeleteStaffSlipsStorageStaffSlipsResponse.noContent().build())));

			} catch (Exception e) {
				asyncResultHandler
						.handle(succeededFuture(
						  StaffSlipsStorageResource.DeleteStaffSlipsStorageStaffSlipsResponse
                .withPlainInternalServerError(e.getMessage())));
			}
		});

	}

	@Override
	public void getStaffSlipsStorageStaffSlips(int offset, int limit, String query, String lang,
			Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

		String tenantId = okapiHeaders.get(TENANT_HEADER);

		try {

			PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
					TenantTool.calculateTenantId(tenantId));

			String[] fieldList = { "*" };

			CQL2PgJSON cql2pgJson = new CQL2PgJSON(STAFF_SLIP_TABLE + ".jsonb");
			CQLWrapper cql = new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));

			postgresClient.get(STAFF_SLIP_TABLE, STAFF_SLIP_CLASS, fieldList, cql, true, false, reply -> {
				try {

					if (reply.succeeded()) {

						@SuppressWarnings("unchecked")
						List<StaffSlip> staffSlips = (List<StaffSlip>) reply.result().getResults();

						StaffSlips pagedStaffSlips = new StaffSlips();
						pagedStaffSlips.setStaffSlips(staffSlips);
						pagedStaffSlips.setTotalRecords((Integer) reply.result().getResultInfo().getTotalRecords());

						asyncResultHandler.handle(succeededFuture(
								StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse.withJsonOK(pagedStaffSlips)));

					} else {
						asyncResultHandler
								.handle(succeededFuture(StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse
										.withPlainInternalServerError(reply.cause().getMessage())));
					}

				} catch (Exception e) {
					log.error(e.getMessage());
					asyncResultHandler
							.handle(succeededFuture(StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse
									.withPlainInternalServerError(e.getMessage())));
				}

			});

		} catch (Exception e) {
			log.error(e.getMessage());
			asyncResultHandler.handle(succeededFuture(StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse
					.withPlainInternalServerError(e.getMessage())));
		}

	}

	@Override
	public void postStaffSlipsStorageStaffSlips(String lang, StaffSlip entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

		String tenantId = okapiHeaders.get(TENANT_HEADER);

		ImmutablePair<Boolean, String> validationResult = validateStaffSlip(entity);

		if (!validationResult.getLeft()) {
			asyncResultHandler.handle(succeededFuture(StaffSlipsStorageResource.PostStaffSlipsStorageStaffSlipsResponse
					.withPlainBadRequest(validationResult.getRight())));

			return;
		}

		createStaffSlip(entity, tenantId, asyncResultHandler, vertxContext);

	}

	@Override
	public void getStaffSlipsStorageStaffSlipsByStaffSlipId(String staffSlipId, String lang,
			Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
			throws Exception {

		String tenantId = okapiHeaders.get(TENANT_HEADER);

		try {

			PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
					TenantTool.calculateTenantId(tenantId));

			Criteria a = new Criteria();

			a.addField("'id'");
			a.setOperation("=");
			a.setValue(staffSlipId);

			Criterion criterion = new Criterion(a);

			vertxContext.runOnContext(v -> {
				try {

					postgresClient.get(STAFF_SLIP_TABLE, STAFF_SLIP_CLASS, criterion, true, false, reply -> {
						if (reply.succeeded()) {

							@SuppressWarnings("unchecked")
							List<StaffSlip> staffSlips = (List<StaffSlip>) reply.result().getResults();

							if (staffSlips.size() == 1) {
								StaffSlip staffSlip = staffSlips.get(0);

								asyncResultHandler.handle(
										succeededFuture(StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
												.withJsonOK(staffSlip)));

							} else {
								asyncResultHandler.handle(
										succeededFuture(StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
												.withPlainNotFound("Not Found")));
							}

						} else {
							asyncResultHandler.handle(
									succeededFuture(StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
											.withPlainInternalServerError(reply.cause().getMessage())));

						}
					});

				} catch (Exception e) {
					log.error(e.getMessage());
					asyncResultHandler.handle(
							succeededFuture(StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
									.withPlainInternalServerError(e.getMessage())));
				}
			});

		} catch (Exception e) {
			log.error(e.getMessage());
			asyncResultHandler
					.handle(succeededFuture(StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
							.withPlainInternalServerError(e.getMessage())));
		}

	}

	@Override
	public void deleteStaffSlipsStorageStaffSlipsByStaffSlipId(String staffSlipId, String lang,
			Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
			throws Exception {

		String tenantId = okapiHeaders.get(TENANT_HEADER);

		try {

			PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
					TenantTool.calculateTenantId(tenantId));

			Criteria c = new Criteria();

			c.addField("'id'");
			c.setOperation("=");
			c.setValue(staffSlipId);

			Criterion criterion = new Criterion(c);

			vertxContext.runOnContext(v -> {
				try {

					postgresClient.delete(STAFF_SLIP_TABLE, criterion, reply -> {
						if (reply.succeeded()) {

							asyncResultHandler.handle(
									succeededFuture(DeleteStaffSlipsStorageStaffSlipsByStaffSlipIdResponse.withNoContent()));

						} else {
							asyncResultHandler.handle(succeededFuture(StaffSlipsStorageResource.DeleteStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
											.withPlainInternalServerError(reply.cause().getMessage())));
						}
					});

				} catch (Exception e) {
					asyncResultHandler.handle(
							succeededFuture(StaffSlipsStorageResource.DeleteStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
									.withPlainInternalServerError(e.getMessage())));
				}
			});

		} catch (Exception e) {
			asyncResultHandler.handle(
					succeededFuture(StaffSlipsStorageResource.DeleteStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
							.withPlainInternalServerError(e.getMessage())));
		}

	}

	@Override
	public void putStaffSlipsStorageStaffSlipsByStaffSlipId(String staffSlipId, String lang, StaffSlip entity,
			Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
			throws Exception {

		String tenantId = okapiHeaders.get(TENANT_HEADER);

		ImmutablePair<Boolean, String> validationResult = validateStaffSlip(entity);

		if (!validationResult.getLeft()) {
			asyncResultHandler.handle(succeededFuture(
					LoanStorageResource.PostLoanStorageLoansResponse.withPlainBadRequest(validationResult.getRight())));

			return;
		}

		try {
			PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
					TenantTool.calculateTenantId(tenantId));

			Criteria c = new Criteria();

			c.addField("'id'");
			c.setOperation("=");
			c.setValue(staffSlipId);

			Criterion criterion = new Criterion(c);

			vertxContext.runOnContext(v -> {
				try {

					postgresClient.get(STAFF_SLIP_TABLE, STAFF_SLIP_CLASS, criterion, true, false, reply -> {
						if (reply.succeeded()) {

							@SuppressWarnings("unchecked")
							List<StaffSlip> staffSlips = (List<StaffSlip>) reply.result().getResults();

							if (staffSlips.size() == 1) {

								try {

									postgresClient.update(STAFF_SLIP_TABLE, entity, criterion, true, update -> {

										try {

											if (update.succeeded()) {

												OutStream stream = new OutStream();
												stream.setData(entity);

												asyncResultHandler.handle(succeededFuture(PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse.withNoContent()));

											} else {
												asyncResultHandler.handle(succeededFuture(
														StaffSlipsStorageResource.PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
																.withPlainInternalServerError(reply.cause().getMessage())));
											}

										} catch (Exception e) {
											asyncResultHandler.handle(succeededFuture(StaffSlipsStorageResource.PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
															.withPlainInternalServerError(e.getMessage())));
										}

									});

								} catch (Exception e) {
									asyncResultHandler.handle(succeededFuture(StaffSlipsStorageResource.PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
													.withPlainInternalServerError(e.getMessage())));
								}

							} else {
								try {
									createStaffSlip(entity, tenantId, asyncResultHandler, vertxContext);
								} catch (Exception e) {
									asyncResultHandler.handle(succeededFuture(StaffSlipsStorageResource.PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
													.withPlainInternalServerError(e.getMessage())));
								}
							}
						} else {
							asyncResultHandler.handle(
									succeededFuture(StaffSlipsStorageResource.PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
											.withPlainInternalServerError(reply.cause().getMessage())));
						}
					});

				} catch (Exception e) {
					asyncResultHandler.handle(
							succeededFuture(StaffSlipsStorageResource.PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
									.withPlainInternalServerError(e.getMessage())));
				}
			});

		} catch (Exception e) {
			asyncResultHandler
					.handle(succeededFuture(StaffSlipsStorageResource.PutStaffSlipsStorageStaffSlipsByStaffSlipIdResponse
							.withPlainInternalServerError(e.getMessage())));
		}

	}

	private void createStaffSlip(StaffSlip entity, String tenantId, Handler<AsyncResult<Response>> asyncResultHandler,
			Context vertxContext) {
		try {

			PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
					TenantTool.calculateTenantId(tenantId));

			vertxContext.runOnContext(v -> {
				try {

					if (entity.getId() == null) {
						entity.setId(UUID.randomUUID().toString());
					}

					if (entity.getActive() == null) {
						entity.setActive(true);
					}

					postgresClient.save(STAFF_SLIP_TABLE, entity.getId(), entity, reply -> {
						try {
							if (reply.succeeded()) {

								OutStream stream = new OutStream();
								stream.setData(entity);

								asyncResultHandler
										.handle(succeededFuture(StaffSlipsStorageResource.PostStaffSlipsStorageStaffSlipsResponse
												.withJsonCreated(reply.result(), stream)));

							} else {
								asyncResultHandler.handle(succeededFuture(LoanStorageResource.PostLoanStorageLoansResponse
										.withPlainInternalServerError(reply.cause().toString())));
							}
						} catch (Exception e) {
							log.error(e.getMessage());
							asyncResultHandler
									.handle(succeededFuture(StaffSlipsStorageResource.PostStaffSlipsStorageStaffSlipsResponse
											.withPlainInternalServerError(e.getMessage())));
						}
					});

				} catch (Exception e) {
					log.error(e.getMessage());
					asyncResultHandler
							.handle(succeededFuture(StaffSlipsStorageResource.PostStaffSlipsStorageStaffSlipsResponse
									.withPlainInternalServerError(e.getMessage())));
				}
			});

		} catch (Exception e) {
			asyncResultHandler.handle(
					succeededFuture(PostStaffSlipsStorageStaffSlipsResponse.withPlainInternalServerError(e.getMessage())));
		}
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
