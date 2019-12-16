package org.folio.rest.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

public class LoanRequestBuilder implements Builder {

  private final UUID id;
  private final UUID itemId;
  private final UUID userId;
  private final UUID proxyUserId;
  private final DateTime loanDate;
  private final String statusName;
  private final String itemStatus;
  private final DateTime dueDate;
  private final String action;
  private final String actionComment;
  private final UUID itemEffectiveLocationAtCheckOut;
  private final UUID loanPolicyId;
  private DateTime returnDate;
  private DateTime systemReturnDate;
  private final Integer renewalCount;
  private Boolean dueDateChangedByRecall;
  private final DateTime declaredLostDate;
  private final UUID effectiveOverdueFinePolicyId;
  private final UUID lostItemPolicyId;

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
    Boolean dueDateChangedByRecall,
    DateTime declaredLostDate,
    UUID effectiveOverdueFinePolicyId,
    UUID lostItemPolicyId) {

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
    this.declaredLostDate = declaredLostDate;
    this.effectiveOverdueFinePolicyId = effectiveOverdueFinePolicyId;
    this.lostItemPolicyId = lostItemPolicyId;
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

    final DateTime declaredLostDate = example.containsKey("declaredLostDate")
      ? DateTime.parse(example.getString("declaredLostDate"))
      : null;

    final UUID effectiveOverdueFinePolicyId = example
      .containsKey("effectiveOverdueFinePolicyId")
      ? UUID.fromString(example.getString("effectiveOverdueFinePolicyId"))
      : null;

    final UUID lostItemPolicyId = example.containsKey("lostItemPolicyId")
      ? UUID.fromString(example.getString("lostItemPolicyId"))
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
      example.getBoolean("dueDateChangedByRecall"),
      declaredLostDate,
      effectiveOverdueFinePolicyId,
      lostItemPolicyId
      );
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
    if (declaredLostDate != null) {
      request.put("declaredLostDate", declaredLostDate.toString(ISODateTimeFormat.dateTime()));
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

    if (effectiveOverdueFinePolicyId != null) {
      request.put("effectiveOverdueFinePolicyId",
        effectiveOverdueFinePolicyId.toString());
    }

    if (lostItemPolicyId != null) {
      request.put("lostItemPolicyId", lostItemPolicyId.toString());
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
  }

  public LoanRequestBuilder withNoUserId() {
    return withUserId(null);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
  }

  public LoanRequestBuilder withActionComment(String actionComment) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.actionComment,
      this.dueDate,
      itemLocation,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
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
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
  }

  public LoanRequestBuilder withRenewalCount(int renewalCount) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
  }

  public LoanRequestBuilder withDueDateChangedByRecall(Boolean dueDateChangedByRecall) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
  }

  public LoanRequestBuilder withDeclaredLostDate(DateTime declaredLostDate) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
  }

  public LoanRequestBuilder withEffectiveOverdueFinePolicyId(
    UUID effectiveOverdueFinePolicyId) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      effectiveOverdueFinePolicyId,
      this.lostItemPolicyId);
  }

  public LoanRequestBuilder withLostItemPolicyId(UUID lostItemPolicyId) {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.proxyUserId,
      this.loanDate,
      this.statusName,
      this.itemStatus,
      this.action,
      this.actionComment,
      this.dueDate,
      this.itemEffectiveLocationAtCheckOut,
      this.loanPolicyId,
      this.returnDate,
      this.systemReturnDate,
      this.renewalCount,
      this.dueDateChangedByRecall,
      this.declaredLostDate,
      this.effectiveOverdueFinePolicyId,
      lostItemPolicyId);
  }
}
