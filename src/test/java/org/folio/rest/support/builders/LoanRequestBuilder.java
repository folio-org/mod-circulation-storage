package org.folio.rest.support.builders;

import static lombok.AccessLevel.PRIVATE;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor(access = PRIVATE)
public class LoanRequestBuilder implements Builder {
  private final UUID id;
  private final UUID itemId;
  private final UUID userId;
  private final UUID proxyUserId;
  private final DateTime loanDate;
  private final String status;
  private final String itemStatus;
  private final String action;
  private final String actionComment;
  private final DateTime dueDate;
  private final UUID itemEffectiveLocationIdAtCheckOut;
  private final UUID loanPolicyId;
  private DateTime returnDate;
  private DateTime systemReturnDate;
  private final Integer renewalCount;
  private final Boolean dueDateChangedByRecall;
  private final DateTime declaredLostDate;
  private final UUID overdueFinePolicyId;
  private final UUID lostItemPolicyId;
  private final DateTime claimedReturnedDate;
  private final JsonObject agedToLostDelayedBilling;

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
      null,
      null,
      null);
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

    final UUID itemEffectiveLocationIdAtCheckOut = example.containsKey("itemEffectiveLocationIdAtCheckOut")
      ? UUID.fromString(example.getString("itemEffectiveLocationIdAtCheckOut"))
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

    final UUID overdueFinePolicyId = example
      .containsKey("overdueFinePolicyId")
      ? UUID.fromString(example.getString("overdueFinePolicyId"))
      : null;

    final UUID lostItemPolicyId = example.containsKey("lostItemPolicyId")
      ? UUID.fromString(example.getString("lostItemPolicyId"))
      : null;

    final DateTime claimedReturnedDate = example.containsKey("claimedReturnedDate")
      ? DateTime.parse(example.getString("claimedReturnedDate"))
      : null;

    final JsonObject agedToLostDelayedBilling = example.containsKey("agedToLostDelayedBilling")
       ? example.getJsonObject("agedToLostDelayedBilling")
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
      itemEffectiveLocationIdAtCheckOut,
      loanPolicyId,
      returnDate,
      systemReturnDate,
      renewalCount,
      example.getBoolean("dueDateChangedByRecall"),
      declaredLostDate,
      overdueFinePolicyId,
      lostItemPolicyId,
      claimedReturnedDate,
      agedToLostDelayedBilling);
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
    if(status != null) {
      request.put("status", new JsonObject().put("name", status));

      if(status.equals("Closed")) {
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

    if(itemEffectiveLocationIdAtCheckOut != null) {
      request.put("itemEffectiveLocationIdAtCheckOut", itemEffectiveLocationIdAtCheckOut.toString());
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

    if (overdueFinePolicyId != null) {
      request.put("overdueFinePolicyId",
        overdueFinePolicyId.toString());
    }

    if (lostItemPolicyId != null) {
      request.put("lostItemPolicyId", lostItemPolicyId.toString());
    }

    if (claimedReturnedDate != null) {
      request.put("claimedReturnedDate", claimedReturnedDate.toString());
    }

     if (agedToLostDelayedBilling != null) {
      request.put("agedToLostDelayedBilling", agedToLostDelayedBilling);
    }

    return request;
  }

  public LoanRequestBuilder withNoId() {
    return withId(null);
  }

  public LoanRequestBuilder withNoUserId() {
    return withUserId(null);
  }

  public LoanRequestBuilder open() {
    return withStatus("Open");
  }

  public LoanRequestBuilder closed() {
    return withStatus("Closed");
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

  public LoanRequestBuilder checkedOut() {
    return open().withAction("checkedout").withItemStatus("Checked out");
  }

  public LoanRequestBuilder checkedIn() {
    return closed().withAction("checkedin").withItemStatus("Checked in");
  }

  public LoanRequestBuilder agedToLost() {
    return open().withAction("itemAgedToLost").withItemStatus("Aged to lost");
  }

  public LoanRequestBuilder lostAndPaid() {
    return closed().withAction("closedLoan").withItemStatus("Lost and paid");
  }

  public LoanRequestBuilder withAgedToLostDelayedBilling(boolean hasBeenBilled,
    DateTime whenToBill, DateTime agedToLostDate) {
    return withAgedToLostDelayedBilling(new JsonObject()
      .put("lostItemHasBeenBilled", hasBeenBilled)
      .put("dateLostItemShouldBeBilled", whenToBill.toString())
      .put("agedToLostDate", agedToLostDate.toString())
    );
  }
}
