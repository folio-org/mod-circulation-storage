package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.StaffSlip;
import org.folio.rest.jaxrs.resource.StaffSlipsStorageResource;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class StaffSlipsAPI implements StaffSlipsStorageResource {

	@Override
	public void deleteStaffSlipsStorageStaffSlips(String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		
		asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
	    		StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse.withNotImplemented()));

	}

	@Override
	public void putStaffSlipsStorageStaffSlips(String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		
		asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
	    		StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse.withNotImplemented()));

	}

	@Override
	public void getStaffSlipsStorageStaffSlips(
			String lang, 
			Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, 
			Context vertxContext) throws Exception {
			    
	    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
	    		StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse.withNotImplemented()));

	}

	@Override
	public void postStaffSlipsStorageStaffSlips(String lang, StaffSlip entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		
		asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
	    		StaffSlipsStorageResource.GetStaffSlipsStorageStaffSlipsResponse.withNotImplemented()));

	}

}
