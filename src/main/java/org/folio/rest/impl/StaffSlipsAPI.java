package org.folio.rest.impl;

import static org.folio.rest.impl.Headers.TENANT_HEADER;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.StaffSlip;
import org.folio.rest.jaxrs.model.StaffSlipsJson;
import org.folio.rest.jaxrs.resource.LoanStorageResource;
import org.folio.rest.jaxrs.resource.StaffSlipsStorageResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class StaffSlipsAPI implements StaffSlipsStorageResource {

	private static final String STAFF_SLIP_TABLE = "staff_slips";
	
	private static final Class<StaffSlip> STAFF_SLIP_CLASS = StaffSlip.class;

	
	private static final Logger log = LoggerFactory.getLogger(StaffSlipsAPI.class);

	@Override
	public void deleteStaffSlipsStorageStaffSlips(String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

		asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
				StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse.withNotImplemented()));

	}

	@Override
	public void getStaffSlipsStorageStaffSlips(
			int offset, 
			int limit, 
			String query, 
			String lang,
			Map<String, String> okapiHeaders, 
			Handler<AsyncResult<Response>> asyncResultHandler, 
			Context vertxContext) {

	    String tenantId = okapiHeaders.get(TENANT_HEADER);
		
	    try {
	    	
	    	PostgresClient postgresClient = PostgresClient.getInstance(
	                vertxContext.owner(), TenantTool.calculateTenantId(tenantId));
	    	
	          String[] fieldList = {"*"};
	          
	          //TODO: Handle query
	          CQL2PgJSON cql2pgJson = new CQL2PgJSON("staffslip.jsonb");
	          CQLWrapper cql = new CQLWrapper(cql2pgJson, query)
	                  .setLimit(new Limit(limit))
	                  .setOffset(new Offset(offset));

	          postgresClient.get(STAFF_SLIP_TABLE, STAFF_SLIP_CLASS, fieldList, cql, 
	            true, false, reply -> {
	        			  try {
	        				  
	        				  if(reply.succeeded()) {
	        					  
	        					  @SuppressWarnings("unchecked")
								List<StaffSlip> staffSlips = (List<StaffSlip>) reply.result().getResults();
	        					  
	        					//TODO: Handle total records
	        					//TODO: Handle Paging
	        					  
	        					StaffSlipsJson staffSlipsJson = new StaffSlipsJson();
	        					staffSlipsJson.setStaffSlips(staffSlips);
	        					  
	        					asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
	        							StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse.
	        			          	withJsonOK(staffSlipsJson)));
	        					  
	        					  
	        				  } else {
	        					  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
	        							  StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse.
    			                      withPlainInternalServerError(reply.cause().getMessage())));
	        				  }
	        				  
	        			  } catch (Exception e) {
	        	                e.printStackTrace();
	        	                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
	        	                	StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse
	        	                		.withPlainInternalServerError(e.getMessage())));
	        	              }
	        			  
	        		  });
	    	
	    } catch (Exception e) {
	        e.printStackTrace();
	        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        		StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse
	            	.withPlainInternalServerError(e.getMessage())));
	      }

	}

	@Override
	public void postStaffSlipsStorageStaffSlips(String lang, StaffSlip entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

		String tenantId = okapiHeaders.get(TENANT_HEADER);

		ImmutablePair<Boolean, String> validationResult = validateStaffSlip(entity);

		if (validationResult.getLeft() == false) {
			asyncResultHandler.handle(io.vertx.core.Future
					.succeededFuture(StaffSlipsStorageResource.PostStaffSlipsStorageStaffSlipsResponse
							.withPlainBadRequest(validationResult.getRight())));

			return;
		}

		try {

			PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
					TenantTool.calculateTenantId(tenantId));

			vertxContext.runOnContext(v -> {
				try {

					if (entity.getId() == null) {
						entity.setId(UUID.randomUUID().toString());
					}

					postgresClient.save(STAFF_SLIP_TABLE, entity.getId(), entity, reply -> {
						try {
							if (reply.succeeded()) {

								OutStream stream = new OutStream();
								stream.setData(entity);

								asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
										StaffSlipsStorageResource.PostStaffSlipsStorageStaffSlipsResponse
												.withJsonCreated(reply.result(), stream)));

							} else {
								asyncResultHandler.handle(io.vertx.core.Future
										.succeededFuture(LoanStorageResource.PostLoanStorageLoansResponse
												.withPlainInternalServerError(reply.cause().toString())));
							}
						} catch (Exception e) {
							e.printStackTrace();
							asyncResultHandler.handle(io.vertx.core.Future
									.succeededFuture(StaffSlipsStorageResource.PostStaffSlipsStorageStaffSlipsResponse
											.withPlainInternalServerError(e.getMessage())));
						}
					});

				} catch (Exception e) {
					e.printStackTrace();
					asyncResultHandler.handle(io.vertx.core.Future
							.succeededFuture(StaffSlipsStorageResource.PostStaffSlipsStorageStaffSlipsResponse
									.withPlainInternalServerError(e.getMessage())));
				}
			});

		} catch (Exception e) {
			asyncResultHandler.handle(Future.succeededFuture(
					PostStaffSlipsStorageStaffSlipsResponse.withPlainInternalServerError(e.getMessage())));
		}

	}

	private ImmutablePair<Boolean, String> validateStaffSlip(StaffSlip staffSlip) {

		Boolean valid = true;
		StringJoiner messages = new StringJoiner("\n");

		if (staffSlip.getName() != null && !staffSlip.getName().equals("")) {
			valid = false;
			messages.add("Name must be a valid string");
		}

		return new ImmutablePair<>(valid, messages.toString());
	}

}
