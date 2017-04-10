package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Loans;
import org.folio.rest.jaxrs.resource.LoanStorageResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.joda.time.DateTime;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;
import java.util.*;

public class LoanStorageAPI implements LoanStorageResource {

  private static final String TENANT_HEADER = "x-okapi-tenant";

  @Override
  public void deleteLoanStorageLoans(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("TRUNCATE TABLE %s_%s.loan",
          tenantId, "circulation_storage"),
          reply -> {
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              LoanStorageResource.DeleteLoanStorageLoansResponse
                .noContent().build()));
          });
      }
      catch(Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          LoanStorageResource.DeleteLoanStorageLoansResponse
            .withPlainInternalServerError(e.getMessage())));
      }
    });
  }

  @Override
  public void getLoanStorageLoans(
    @DefaultValue("0") @Min(0L) @Max(1000L) int offset,
    @DefaultValue("10") @Min(1L) @Max(100L) int limit,
    String query,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

          System.out.println("CQL Query: " + query);

          String[] fieldList = {"*"};

          CQL2PgJSON cql2pgJson = new CQL2PgJSON("loan.jsonb");
          CQLWrapper cql = new CQLWrapper(cql2pgJson, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));

          postgresClient.get("loan", Loan.class, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  List<Loan> loans = (List<Loan>) reply.result()[0];

                  Loans pagedLoans = new Loans();
                  pagedLoans.setLoans(loans);
                  pagedLoans.setTotalRecords((Integer)reply.result()[1]);

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    LoanStorageResource.GetLoanStorageLoansResponse.
                      withJsonOK(pagedLoans)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    LoanStorageResource.GetLoanStorageLoansResponse.
                      withPlainInternalServerError(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  LoanStorageResource.GetLoanStorageLoansResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanStorageResource.GetLoanStorageLoansResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanStorageResource.GetLoanStorageLoansResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void postLoanStorageLoans(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Loan entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    ImmutablePair<Boolean, String> validationResult = validateLoan(entity);

    if(validationResult.getLeft() == false) {
      asyncResultHandler.handle(
        io.vertx.core.Future.succeededFuture(
          LoanStorageResource.PostLoanStorageLoansResponse
            .withPlainBadRequest(
              validationResult.getRight())));

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {

          if(entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
          }

          postgresClient.save("loan", entity.getId(), entity,
            reply -> {
              try {
                if(reply.succeeded()) {
                  OutStream stream = new OutStream();
                  stream.setData(entity);

                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      LoanStorageResource.PostLoanStorageLoansResponse
                        .withJsonCreated(reply.result(), stream)));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      LoanStorageResource.PostLoanStorageLoansResponse
                        .withPlainBadRequest("ID must both be a UUID")));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    LoanStorageResource.PostLoanStorageLoansResponse
                      .withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanStorageResource.PostLoanStorageLoansResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanStorageResource.PostLoanStorageLoansResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void getLoanStorageLoansByLoanId(
    @NotNull String loanId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(loanId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get("loan", Loan.class, criterion, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<Loan> loans = (List<Loan>) reply.result()[0];

                  if (loans.size() == 1) {
                    Loan loan = loans.get(0);

                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        LoanStorageResource.GetLoanStorageLoansByLoanIdResponse.
                          withJsonOK(loan)));
                  }
                  else {
                    asyncResultHandler.handle(
                      Future.succeededFuture(
                        LoanStorageResource.GetLoanStorageLoansByLoanIdResponse.
                          withPlainNotFound("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      LoanStorageResource.GetLoanStorageLoansByLoanIdResponse.
                        withPlainInternalServerError(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  LoanStorageResource.GetLoanStorageLoansByLoanIdResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            LoanStorageResource.GetLoanStorageLoansByLoanIdResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        LoanStorageResource.GetLoanStorageLoansByLoanIdResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void deleteLoanStorageLoansByLoanId(
    @NotNull String loanId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(loanId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete("loan", criterion,
            reply -> {
              if(reply.succeeded()) {
                asyncResultHandler.handle(
                  Future.succeededFuture(
                    DeleteLoanStorageLoansByLoanIdResponse
                      .withNoContent()));
              }
              else {
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteLoanStorageLoansByLoanIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLoanStorageLoansByLoanIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteLoanStorageLoansByLoanIdResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void putLoanStorageLoansByLoanId(
    @NotNull String loanId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Loan entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    ImmutablePair<Boolean, String> validationResult = validateLoan(entity);

    if(validationResult.getLeft() == false) {
      asyncResultHandler.handle(
        io.vertx.core.Future.succeededFuture(
          LoanStorageResource.PostLoanStorageLoansResponse
            .withPlainBadRequest(
              validationResult.getRight())));

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(loanId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get("loan", Loan.class, criterion, true, false,
            reply -> {
              if(reply.succeeded()) {
                List<Loan> loanList = (List<Loan>) reply.result()[0];

                if (loanList.size() == 1) {
                  try {
                    postgresClient.update("loan", entity, criterion,
                      true,
                      update -> {
                        try {
                          if(update.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanStorageLoansByLoanIdResponse
                                  .withNoContent()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanStorageLoansByLoanIdResponse
                                  .withPlainInternalServerError(
                                    update.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutLoanStorageLoansByLoanIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutLoanStorageLoansByLoanIdResponse
                        .withPlainInternalServerError(e.getMessage())));
                  }
                }
                else {
                  try {
                    postgresClient.save("loan", entity.getId(), entity,
                      save -> {
                        try {
                          if(save.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanStorageLoansByLoanIdResponse
                                  .withNoContent()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutLoanStorageLoansByLoanIdResponse
                                  .withPlainInternalServerError(
                                    save.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutLoanStorageLoansByLoanIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutLoanStorageLoansByLoanIdResponse
                        .withPlainInternalServerError(e.getMessage())));
                  }
                }
              } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutLoanStorageLoansByLoanIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutLoanStorageLoansByLoanIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        PutLoanStorageLoansByLoanIdResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  private ImmutablePair<Boolean, String> validateLoan(Loan loan) {

    Boolean valid = true;
    StringJoiner messages = new StringJoiner("\n");

    //ISO8601 is less strict than RFC3339 so will not catch some issues
    try {
      DateTime.parse(loan.getLoanDate());
    }
    catch(Exception e) {
      valid = false;
      messages.add("loan date must be a date time (in RFC3339 format)");
    }

    if(loan.getReturnDate() != null) {
      //ISO8601 is less strict than RFC3339 so will not catch some issues
      try {
        DateTime.parse(loan.getReturnDate());
      }
      catch(Exception e) {
        valid = false;
        messages.add("return date must be a date time (in RFC3339 format)");
      }
    }

    return new ImmutablePair<>(valid, messages.toString());
  }
}
