
package org.folio.rest.jaxrs.resource;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import io.vertx.core.Context;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Loans;

@Path("loan-storage")
public interface LoanStorageResource {


    /**
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @DELETE
    @Path("loans")
    @Produces({
        "text/plain"
    })
    @Validate
    void deleteLoanStorageLoans(
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Retrieve a list of loan items.
     * 
     * @param offset
     *     Skip over a number of elements by specifying an offset value for the query e.g. 0
     * @param limit
     *     Limit the number of elements returned in the response e.g. 10
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @GET
    @Path("loans")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getLoanStorageLoans(
        @QueryParam("offset")
        @DefaultValue("0")
        @Min(0L)
        @Max(1000L)
        int offset,
        @QueryParam("limit")
        @DefaultValue("10")
        @Min(1L)
        @Max(100L)
        int limit,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Create a new loan item.
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     * @param entity
     *      e.g. {
     *       "id": "cf23adf0-61ba-4887-bf82-956c4aae2260",
     *       "userId": "df7f4993-8c14-4a0f-ab63-93975ab01c76",
     *       "itemId": "cb20f34f-b773-462f-a091-b233cc96b9e6",
     *       "loanDate": "2017-03-01T23:11:00-01:00"
     *     }
     *     
     */
    @POST
    @Path("loans")
    @Consumes("application/json")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void postLoanStorageLoans(
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Loan entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Retrieve loan item with given {loanId}
     * 
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     * @param loanId
     *     
     */
    @GET
    @Path("loans/{loanId}")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getLoanStorageLoansByLoanId(
        @PathParam("loanId")
        @NotNull
        String loanId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Delete loan item with given {loanId}
     * 
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     * @param loanId
     *     
     */
    @DELETE
    @Path("loans/{loanId}")
    @Produces({
        "text/plain"
    })
    @Validate
    void deleteLoanStorageLoansByLoanId(
        @PathParam("loanId")
        @NotNull
        String loanId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Update loan item with given {loanId}
     * 
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     * @param loanId
     *     
     * @param entity
     *      e.g. {
     *       "id": "cf23adf0-61ba-4887-bf82-956c4aae2260",
     *       "userId": "df7f4993-8c14-4a0f-ab63-93975ab01c76",
     *       "itemId": "cb20f34f-b773-462f-a091-b233cc96b9e6",
     *       "loanDate": "2017-03-01T23:11:00-01:00"
     *     }
     *     
     */
    @PUT
    @Path("loans/{loanId}")
    @Consumes("application/json")
    @Produces({
        "text/plain"
    })
    @Validate
    void putLoanStorageLoansByLoanId(
        @PathParam("loanId")
        @NotNull
        String loanId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Loan entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    public class DeleteLoanStorageLoansByLoanIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private DeleteLoanStorageLoansByLoanIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Not implemented yet
         * 
         */
        public static LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse withNotImplemented() {
            Response.ResponseBuilder responseBuilder = Response.status(501);
            return new LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Item deleted successfully
         * 
         */
        public static LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "loan not found"
         * 
         * 
         * @param entity
         *     "loan not found"
         *     
         */
        public static LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to delete loan -- constraint violation"
         * 
         * 
         * @param entity
         *     "unable to delete loan -- constraint violation"
         *     
         */
        public static LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.DeleteLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

    }

    public class DeleteLoanStorageLoansResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private DeleteLoanStorageLoansResponse(Response delegate) {
            super(delegate);
        }

        /**
         * All loans deleted
         * 
         */
        public static LoanStorageResource.DeleteLoanStorageLoansResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new LoanStorageResource.DeleteLoanStorageLoansResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static LoanStorageResource.DeleteLoanStorageLoansResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.DeleteLoanStorageLoansResponse(responseBuilder.build());
        }

    }

    public class GetLoanStorageLoansByLoanIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetLoanStorageLoansByLoanIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Not implemented yet
         * 
         */
        public static LoanStorageResource.GetLoanStorageLoansByLoanIdResponse withNotImplemented() {
            Response.ResponseBuilder responseBuilder = Response.status(501);
            return new LoanStorageResource.GetLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Returns item with a given ID e.g. {
         *   "id": "cf23adf0-61ba-4887-bf82-956c4aae2260",
         *   "userId": "df7f4993-8c14-4a0f-ab63-93975ab01c76",
         *   "itemId": "cb20f34f-b773-462f-a091-b233cc96b9e6",
         *   "loanDate": "2017-03-01T23:11:00-01:00"
         * }
         * 
         * 
         * @param entity
         *     {
         *       "id": "cf23adf0-61ba-4887-bf82-956c4aae2260",
         *       "userId": "df7f4993-8c14-4a0f-ab63-93975ab01c76",
         *       "itemId": "cb20f34f-b773-462f-a091-b233cc96b9e6",
         *       "loanDate": "2017-03-01T23:11:00-01:00"
         *     }
         *     
         */
        public static LoanStorageResource.GetLoanStorageLoansByLoanIdResponse withJsonOK(Loan entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new LoanStorageResource.GetLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "loan not found"
         * 
         * 
         * @param entity
         *     "loan not found"
         *     
         */
        public static LoanStorageResource.GetLoanStorageLoansByLoanIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.GetLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static LoanStorageResource.GetLoanStorageLoansByLoanIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.GetLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

    }

    public class GetLoanStorageLoansResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetLoanStorageLoansResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Not implemented yet
         * 
         */
        public static LoanStorageResource.GetLoanStorageLoansResponse withNotImplemented() {
            Response.ResponseBuilder responseBuilder = Response.status(501);
            return new LoanStorageResource.GetLoanStorageLoansResponse(responseBuilder.build());
        }

        /**
         * Returns a list of loan items e.g. {
         * "loans": [
         *   {
         *     "id": "cf23adf0-61ba-4887-bf82-956c4aae2260",
         *     "userId": "df7f4993-8c14-4a0f-ab63-93975ab01c76",
         *     "itemId": "cb20f34f-b773-462f-a091-b233cc96b9e6",
         *     "loanDate": "2017-03-01T22:34:11-4:00"
         *   },
         *   {
         *     "id": "1d09af65-aeaa-499c-80cb-d52847b75a60",
         *     "userId": "15054e48-03e8-4ed5-810b-7192b86accab",
         *     "itemId": "94838fa2-288a-45c2-ad19-9102f5645127",
         *     "loanDate": "2017-01-14T19:14:36-01:00",
         *     "returnDate": "2017-01-16T09:15:23-01:00"
         *   }
         * ],
         * "totalRecords": 2
         * }
         * 
         * 
         * @param entity
         *     {
         *     "loans": [
         *       {
         *         "id": "cf23adf0-61ba-4887-bf82-956c4aae2260",
         *         "userId": "df7f4993-8c14-4a0f-ab63-93975ab01c76",
         *         "itemId": "cb20f34f-b773-462f-a091-b233cc96b9e6",
         *         "loanDate": "2017-03-01T22:34:11-4:00"
         *       },
         *       {
         *         "id": "1d09af65-aeaa-499c-80cb-d52847b75a60",
         *         "userId": "15054e48-03e8-4ed5-810b-7192b86accab",
         *         "itemId": "94838fa2-288a-45c2-ad19-9102f5645127",
         *         "loanDate": "2017-01-14T19:14:36-01:00",
         *         "returnDate": "2017-01-16T09:15:23-01:00"
         *       }
         *     ],
         *     "totalRecords": 2
         *     }
         *     
         */
        public static LoanStorageResource.GetLoanStorageLoansResponse withJsonOK(Loans entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new LoanStorageResource.GetLoanStorageLoansResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. unable to list loans -- malformed parameter 'query', syntax error at column 6
         * 
         * @param entity
         *     unable to list loans -- malformed parameter 'query', syntax error at column 6
         */
        public static LoanStorageResource.GetLoanStorageLoansResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.GetLoanStorageLoansResponse(responseBuilder.build());
        }

        /**
         * Not authorized to perform requested action e.g. unable to list loans -- unauthorized
         * 
         * @param entity
         *     unable to list loans -- unauthorized
         */
        public static LoanStorageResource.GetLoanStorageLoansResponse withPlainUnauthorized(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(401).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.GetLoanStorageLoansResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static LoanStorageResource.GetLoanStorageLoansResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.GetLoanStorageLoansResponse(responseBuilder.build());
        }

    }

    public class PostLoanStorageLoansResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostLoanStorageLoansResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a newly created item, with server-controlled fields like 'id' populated e.g. {
         *   "id": "cf23adf0-61ba-4887-bf82-956c4aae2260",
         *   "userId": "df7f4993-8c14-4a0f-ab63-93975ab01c76",
         *   "itemId": "cb20f34f-b773-462f-a091-b233cc96b9e6",
         *   "loanDate": "2017-03-01T23:11:00-01:00"
         * }
         * 
         * 
         * @param location
         *     URI to the created loan item
         * @param entity
         *     {
         *       "id": "cf23adf0-61ba-4887-bf82-956c4aae2260",
         *       "userId": "df7f4993-8c14-4a0f-ab63-93975ab01c76",
         *       "itemId": "cb20f34f-b773-462f-a091-b233cc96b9e6",
         *       "loanDate": "2017-03-01T23:11:00-01:00"
         *     }
         *     
         */
        public static LoanStorageResource.PostLoanStorageLoansResponse withJsonCreated(String location, StreamingOutput entity) {
            Response.ResponseBuilder responseBuilder = Response.status(201).header("Content-Type", "application/json").header("Location", location);
            responseBuilder.entity(entity);
            return new LoanStorageResource.PostLoanStorageLoansResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to add loan -- malformed JSON at 13:3"
         * 
         * 
         * @param entity
         *     "unable to add loan -- malformed JSON at 13:3"
         *     
         */
        public static LoanStorageResource.PostLoanStorageLoansResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.PostLoanStorageLoansResponse(responseBuilder.build());
        }

        /**
         * Not authorized to perform requested action e.g. unable to create loans -- unauthorized
         * 
         * @param entity
         *     unable to create loans -- unauthorized
         */
        public static LoanStorageResource.PostLoanStorageLoansResponse withPlainUnauthorized(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(401).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.PostLoanStorageLoansResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static LoanStorageResource.PostLoanStorageLoansResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.PostLoanStorageLoansResponse(responseBuilder.build());
        }

    }

    public class PutLoanStorageLoansByLoanIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PutLoanStorageLoansByLoanIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Not implemented yet
         * 
         */
        public static LoanStorageResource.PutLoanStorageLoansByLoanIdResponse withNotImplemented() {
            Response.ResponseBuilder responseBuilder = Response.status(501);
            return new LoanStorageResource.PutLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Item successfully updated
         * 
         */
        public static LoanStorageResource.PutLoanStorageLoansByLoanIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new LoanStorageResource.PutLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "loan not found"
         * 
         * 
         * @param entity
         *     "loan not found"
         *     
         */
        public static LoanStorageResource.PutLoanStorageLoansByLoanIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.PutLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to update loan -- malformed JSON at 13:4"
         * 
         * 
         * @param entity
         *     "unable to update loan -- malformed JSON at 13:4"
         *     
         */
        public static LoanStorageResource.PutLoanStorageLoansByLoanIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.PutLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static LoanStorageResource.PutLoanStorageLoansByLoanIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new LoanStorageResource.PutLoanStorageLoansByLoanIdResponse(responseBuilder.build());
        }

    }

}
