package org.folio.rest.support.builders;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import io.vertx.core.json.JsonObject;

public class LoanRequestBuilder implements Builder {

  private UUID id;
  private UUID itemId;
  private UUID userId;
  private UUID proxyUserId;
  private DateTime loanDate;
  private String statusName;
  private String itemStatus;
  private DateTime dueDate;
  private String action;
  private String actionComment;
  private UUID itemEffectiveLocationAtCheckOut;
  private UUID loanPolicyId;
  private DateTime returnDate;
  private DateTime systemReturnDate;
  private Integer renewalCount;
  private Boolean dueDateChangedByRecall;
  private String declaredLostDate;

  public LoanRequestBuilder() {
    this(UUID.randomUUID(),
      UUID.randomUUID(),
      UUID.randomUUID(),
      null,
      new DateTime(2017, 7, 15, 9, 35, 27, DateTimeZone.UTC),
      "Open",
      null,
      "checkedout",
      "test",
      null,
      null,
      null,
      null,
      null,
      null,
      null);
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
    String actionComment,
    DateTime dueDate,
    UUID itemEffectiveLocationAtCheckOut,
    UUID loanPolicyId,
    DateTime returnDate,
    DateTime systemReturnDate,
    Integer renewalCount,
    Boolean dueDateChangedByRecall) {

    this.id = id;
    this.itemId = itemId;
    this.userId = userId;
    this.proxyUserId = proxyUserId;
    this.loanDate = loanDate;
    this.statusName = statusName;
    this.itemStatus = itemStatus;
    this.action = action;
    this.actionComment = actionComment;
    this.dueDate = dueDate;
    this.itemEffectiveLocationAtCheckOut = itemEffectiveLocationAtCheckOut;
    this.loanPolicyId = loanPolicyId;
    this.returnDate = returnDate;
    this.systemReturnDate = systemReturnDate;
    this.renewalCount = renewalCount;
    this.dueDateChangedByRecall = dueDateChangedByRecall;
  }

  public static LoanRequestBuilder from(JsonObject example) {
    //TODO: Extract constants for properties

    final UUID id = example.containsKey("id")
      ? UUID.fromString(example.getString("id"))
      : null;

    final UUID userId = example.containsKey("userId")
      ? UUID.fromString(example.getString("userId"))
      : null;

    final UUID itemId = example.containsKey("itemId")
      ? UUID.fromString(example.getString("itemId"))
      : null;

    final UUID proxyUserId = example.containsKey("proxyUserId")
      ? UUID.fromString(example.getString("proxyUserId"))
      : null;

    final UUID loanPolicyId = example.containsKey("loanPolicyId")
      ? UUID.fromString(example.getString("loanPolicyId"))
      : null;

    final DateTime loanDate = example.containsKey("loanDate")
      ? DateTime.parse(example.getString("loanDate"))
      : null;

    final DateTime dueDate = example.containsKey("dueDate")
      ? DateTime.parse(example.getString("dueDate"))
      : null;

    final UUID itemEffectiveLocationAtCheckOut = example.containsKey("itemEffectiveLocationAtCheckOut")
      ? UUID.fromString(example.getString("itemEffectiveLocationAtCheckOut"))
      : null;

    final DateTime returnDate = example.containsKey("returnDate")
      ? DateTime.parse(example.getString("returnDate"))
      : null;

    final DateTime systemReturnDate = example.containsKey("systemReturnDate")
      ? DateTime.parse(example.getString("systemReturnDate"))
      : null;

    final String statusName = example.containsKey("status")
      ? example.getJsonObject("status").getString("name")
      : null;

    final Integer renewalCount = example.containsKey("renewalCount")
      ? example.getInteger("renewalCount")
      : null;

    final String declaredLostDate = example.containsKey("declaredLostDate")
      ? example.getString("declaredLostDate")
      : null;

    return new LoanRequestBuilder(
      id,
      itemId,
      userId,
      proxyUserId,
      loanDate,
      statusName,
      example.getString("itemStatus"),
      example.getString("action"),
      example.getString("actionComment"),
      dueDate,
      itemEffectiveLocationAtCheckOut,
      loanPolicyId,
      returnDate,
      systemReturnDate,
      renewalCount,
      example.getBoolean("dueDateChangedByRecall"))
      .withDeclaredLostDate(declaredLostDate);
  }

  @Override
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
      request.put("declaredLostDate", declaredLostDate);
    }

    if(itemEffectiveLocationAtCheckOut != null) {
      request.put("itemEffectiveLocationAtCheckOut", itemEffectiveLocationAtCheckOut.toString());
    }

    if(loanPolicyId != null) {
      request.put("loanPolicyId", loanPolicyId.toString());
    }

    if(dueDate != null) {
      request.put("dueDate", formatDateTime(dueDate));
    }

    if(renewalCount != null) {
      request.put("renewalCount", renewalCount);
    }

    if(actionComment != null) {
      request.put("actionComment", actionComment);
    }

    if (dueDateChangedByRecall != null) {
      request.put("dueDateChangedByRecall", dueDateChangedByRecall);
    }

    return request;
  }

  public LoanRequestBuilder withId(UUID newId) {
    this.id = newId;
    return this;
  }

  public LoanRequestBuilder withNoId() {
    return withId(null);
  }

  public LoanRequestBuilder withItemId(UUID itemId) {
    this.itemId = itemId;
    return this;
  }

  public LoanRequestBuilder withUserId(UUID userId) {
    this.userId = userId;
    return this;
  }

  public LoanRequestBuilder withNoUserId() {
    return withUserId(null);
  }

  public LoanRequestBuilder withProxyUserId(UUID proxyUserId) {
    this.proxyUserId = proxyUserId;
    return this;
  }

  public LoanRequestBuilder withLoanDate(DateTime loanDate) {
    this.loanDate = loanDate;
    return this;
  }

  public LoanRequestBuilder withStatus(String statusName) {
    this.statusName = statusName;
    return this;
  }

  public LoanRequestBuilder open() {
    return withStatus("Open");
  }

  public LoanRequestBuilder closed() {
    return withStatus("Closed");
  }

  public LoanRequestBuilder withItemStatus(String itemStatus) {
    this.itemStatus = itemStatus;
    return this;
  }

  public LoanRequestBuilder withAction(String action) {
    this.action = action;
    return this;
  }

  public LoanRequestBuilder withActionComment(String actionComment) {
    this.actionComment = actionComment;
    return this;
  }

  public LoanRequestBuilder withDueDate(DateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public LoanRequestBuilder withReturnDate(DateTime returnDate) {
    this.returnDate = returnDate;
    return this;
  }

  public LoanRequestBuilder withSystemReturnDate(DateTime systemReturnDate) {
    this.systemReturnDate = systemReturnDate;
    return this;
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

  public LoanRequestBuilder withItemEffectiveLocationAtCheckOut(UUID itemLocation) {
    this.itemEffectiveLocationAtCheckOut = itemLocation;
    return this;
  }
  public LoanRequestBuilder withLoanPolicyId(UUID loanPolicyId) {
    this.loanPolicyId = loanPolicyId;
    return this;
  }

  public LoanRequestBuilder withDeclaredLostDate(String date) {
    this.declaredLostDate = date;
    return this;
  }

  public LoanRequestBuilder withRenewalCount(int renewalCount) {
    this.renewalCount = renewalCount;
    return this;
  }

  public LoanRequestBuilder withDueDateChangedByRecall(Boolean dueDateChangedByRecall) {
    this.dueDateChangedByRecall = dueDateChangedByRecall;
    return this;
  }
}
