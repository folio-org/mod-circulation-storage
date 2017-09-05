package org.folio.rest.support.builders;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;

public class RequestRequestBuilder {

  private final UUID id;
  private final String requestType;
  private final DateTime requestDate;
  private final UUID itemId;
  private final UUID requesterId;
  private final String fulfilmentPreference;
  private final LocalDate requestExpirationDate;
  private final LocalDate holdShelfExpirationDate;
  private final ItemSummary itemSummary;

  public RequestRequestBuilder() {
    this(UUID.randomUUID(),
      "Hold",
      new DateTime(2017, 7, 15, 9, 35, 27, DateTimeZone.UTC),
      UUID.randomUUID(),
      UUID.randomUUID(),
      "Hold Shelf",
      null,
      null,
      null);
  }

  private RequestRequestBuilder(
    UUID id,
    String requestType,
    DateTime requestDate,
    UUID itemId,
    UUID requesterId,
    String fulfilmentPreference,
    LocalDate requestExpirationDate, LocalDate holdShelfExpirationDate,
    ItemSummary itemSummary) {

    this.id = id;
    this.requestType = requestType;
    this.requestDate = requestDate;
    this.itemId = itemId;
    this.requesterId = requesterId;
    this.fulfilmentPreference = fulfilmentPreference;
    this.requestExpirationDate = requestExpirationDate;
    this.holdShelfExpirationDate = holdShelfExpirationDate;
    this.itemSummary = itemSummary;
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(this.id != null) {
      request.put("id", this.id.toString());
    }

    request.put("requestType", this.requestType);
    request.put("requestDate", formatDateTime(this.requestDate));
    request.put("itemId", this.itemId.toString());
    request.put("requesterId", this.requesterId.toString());
    request.put("fulfilmentPreference", this.fulfilmentPreference);

    if(requestExpirationDate != null) {
      request.put("requestExpirationDate",
        formatDateOnly(this.requestExpirationDate));
    }

    if(holdShelfExpirationDate != null) {
      request.put("holdShelfExpirationDate",
        formatDateOnly(this.holdShelfExpirationDate));
    }

    if(itemSummary != null) {
      request.put("item", new JsonObject()
          .put("title", itemSummary.title)
          .put("barcode", itemSummary.barcode));
    }

    return request;
  }

  public RequestRequestBuilder recall() {
    return new RequestRequestBuilder(
      this.id,
      "Recall",
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary);
  }

  public RequestRequestBuilder withId(UUID newId) {
    return new RequestRequestBuilder(
      newId,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary);
  }

  public RequestRequestBuilder withNoId() {
    return new RequestRequestBuilder(
      null,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary);
  }

  public RequestRequestBuilder withRequestDate(DateTime requestDate) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary);
  }

  public RequestRequestBuilder withItemId(UUID itemId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary);
  }

  public RequestRequestBuilder withRequesterId(UUID requesterId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      requesterId,
      this.fulfilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary);
  }

  public RequestRequestBuilder fulfilToHoldShelf() {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      "Hold Shelf",
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary);
  }

  public RequestRequestBuilder withRequestExpiration(LocalDate requestExpiration) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      requestExpiration,
      this.holdShelfExpirationDate,
      this.itemSummary);
  }

  public RequestRequestBuilder withHoldShelfExpiration(LocalDate holdShelfExpiration) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.requestExpirationDate,
      holdShelfExpiration,
      this.itemSummary);
  }

  public RequestRequestBuilder withItem(String title, String barcode) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      new ItemSummary(title, barcode));
  }

  private String formatDateTime(DateTime requestDate) {
    return requestDate.toString(ISODateTimeFormat.dateTime());
  }

  private String formatDateOnly(LocalDate date) {
    return date.toString(DateTimeFormat.forPattern("yyyy-MM-dd"));
  }

  private class ItemSummary {
    public final String title;
    public final String barcode;

    public ItemSummary(String title, String barcode) {
      this.title = title;
      this.barcode = barcode;
    }
  }
}
