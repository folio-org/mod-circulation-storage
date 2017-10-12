package org.folio.rest.support.builders;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;

public class LoanRequestBuilder {

  private final UUID id;
  private final UUID itemId;
  private final UUID userId;
  private final UUID proxyUserId;
  private final DateTime loanDate;
  private final String statusName;
  private final DateTime dueDate;
  private final String action;

  public LoanRequestBuilder() {
    this(UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      null,
      new DateTime(2017, 7, 15, 9, 35, 27, DateTimeZone.UTC),
      "Open",
      null,
      "checkedout");
  }

  private LoanRequestBuilder(
    UUID id,
    UUID itemId,
    UUID userId,
    UUID proxyUserId,
    DateTime loanDate,
    String statusName,
    DateTime dueDate,
    String action) {

    this.id = id;
    this.itemId = itemId;
    this.userId = userId;
    this.proxyUserId = proxyUserId;
    this.loanDate = loanDate;
    this.statusName = statusName;
    this.dueDate = dueDate;
    this.action = action;
  }

  public JsonObject create() {

    JsonObject request = new JsonObject();

    if(id != null) {
      request.put("id", id.toString());
    }

    request
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("loanDate", loanDate.toString(ISODateTimeFormat.dateTime()))
      .put("action", action);

    if(proxyUserId != null) {
      request.put("proxyUserId", proxyUserId.toString());
    }

    if(statusName != null) {
      request.put("status", new JsonObject().put("name", statusName));

      if(statusName == "Closed") {
        request.put("returnDate",
          loanDate.plusDays(1).plusHours(4).toString(ISODateTimeFormat.dateTime()));
      }
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
      this.dueDate,
      this.action);
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
      this.dueDate,
      this.action);
  }

  public LoanRequestBuilder withUserId(UUID userId) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.dueDate,
      this.action);
  }

  public LoanRequestBuilder withProxyUserId(UUID proxyUserId) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      proxyUserId,
      this.loanDate,
      this.statusName,
      this.dueDate,
      this.action);
  }

  public LoanRequestBuilder withLoanDate(DateTime loanDate) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      loanDate,
      this.statusName,
      this.dueDate,
      this.action);
  }

  public LoanRequestBuilder withStatus(String statusName) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      statusName,
      this.dueDate,
      this.action);
  }

  public LoanRequestBuilder withAction(String action) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.dueDate,
      action);
  }

  public LoanRequestBuilder withdueDate(DateTime dueDate) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      dueDate,
      this.action);
  }

  public LoanRequestBuilder withNoStatus() {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      null,
      this.dueDate,
      this.action);
  }

  private String formatDateTime(DateTime requestDate) {
    return requestDate.toString(ISODateTimeFormat.dateTime());
  }
}
