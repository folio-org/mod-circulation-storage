package org.folio.rest.support.builders;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import io.vertx.core.json.JsonObject;

public class LoanRequestBuilder {
  private final UUID id;
  private final UUID itemId;
  private final UUID userId;
  private final UUID proxyUserId;
  private final DateTime loanDate;
  private final String statusName;
  private final String itemStatus;
  private final DateTime dueDate;
  private final String action;
  private final UUID loanPolicyId;
  private DateTime returnDate;
  private DateTime systemReturnDate;

  public LoanRequestBuilder() {
    this(UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      null,
      new DateTime(2017, 7, 15, 9, 35, 27, DateTimeZone.UTC),
      "Open",
      null,
      "checkedout",
      null,
      null,
      null,
      null
    );
  }

  private LoanRequestBuilder(
    UUID id,
    UUID itemId,
    UUID userId,
    UUID proxyUserId,
    DateTime loanDate,
    String statusName,
    String itemStatus,
    String action,
    DateTime dueDate,
    UUID loanPolicyId,
    DateTime returnDate,
    DateTime systemReturnDate) {

    this.id = id;
    this.itemId = itemId;
    this.userId = userId;
    this.proxyUserId = proxyUserId;
    this.loanDate = loanDate;
    this.statusName = statusName;
    this.itemStatus = itemStatus;
    this.dueDate = dueDate;
    this.action = action;
    this.loanPolicyId = loanPolicyId;
    this.returnDate = returnDate;
    this.systemReturnDate = systemReturnDate;
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(id != null) {
      request.put("id", id.toString());
    }

    request
      .put("itemId", itemId.toString())
      .put("loanDate", loanDate.toString(ISODateTimeFormat.dateTime()))
      .put("action", action);

    if(itemStatus != null) {
      request.put("itemStatus", itemStatus);
    }

    if(userId != null) {
      request.put("userId", userId.toString());
    }

    if(proxyUserId != null) {
      request.put("proxyUserId", proxyUserId.toString());
    }

    if(statusName != null) {
      request.put("status", new JsonObject().put("name", statusName));

      if(statusName.equals("Closed")) {
        if(returnDate == null) {
          returnDate = loanDate.plusDays(1).plusHours(4);
        }

        if(systemReturnDate == null) {
          systemReturnDate = loanDate.plusDays(1).plusHours(5).plusMinutes(11);
        }

        request.put("returnDate", returnDate.toString(ISODateTimeFormat.dateTime()));
        request.put("systemReturnDate", systemReturnDate.toString(ISODateTimeFormat.dateTime()));
      }
    }

    if(loanPolicyId != null) {
      request.put("loanPolicyId", loanPolicyId.toString());
    }

    if(dueDate != null) {
      request.put("dueDate", formatDateTime(dueDate));
    }

    return request;
  }

  public LoanRequestBuilder withId(UUID newId) {
    return new LoanRequestBuilder(
      newId,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withNoId() {
    return withId(null);
  }

  public LoanRequestBuilder withItemId(UUID itemId) {
    return new LoanRequestBuilder(
      this.id,
      itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withUserId(UUID userId) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withProxyUserId(UUID proxyUserId) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withLoanDate(DateTime loanDate) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withStatus(String statusName) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder open() {
    return withStatus("Open");
  }

  public LoanRequestBuilder closed() {
    return withStatus("Closed");
  }

  public LoanRequestBuilder withItemStatus(String itemStatus) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      statusName,
      itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withAction(String action) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withDueDate(DateTime dueDate) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      dueDate,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withReturnDate(DateTime returnDate) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      returnDate,
      this.systemReturnDate);
  }

  public LoanRequestBuilder withSystemReturnDate(DateTime systemReturnDate) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      this.loanPolicyId,
      this.returnDate,
      systemReturnDate);
  }

  public LoanRequestBuilder withNoStatus() {
    return withStatus(null);
  }

  public LoanRequestBuilder withNoItemStatus() {
    return withItemStatus(null);
  }

  private String formatDateTime(DateTime requestDate) {
    return requestDate.toString(ISODateTimeFormat.dateTime());
  }

  public LoanRequestBuilder withLoanPolicyId(UUID loanPolicyId) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.dueDate,
      loanPolicyId,
      this.returnDate,
      this.systemReturnDate);
  }
}
