package org.folio.rest.support.builders;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;

public class RequestRequestBuilder {

  public static final String OPEN_NOT_YET_FILLED = "Open - Not yet filled";
  public static final String OPEN_AWAITING_PICKUP = "Open - Awaiting pickup";
  public static final String CLOSED_FILLED = "Closed - Filled";

  private final UUID id;
  private final String requestType;
  private final DateTime requestDate;
  private final UUID itemId;
  private final UUID requesterId;
  private final UUID proxyId;
  private final String fulfilmentPreference;
  private final UUID deliveryAddressTypeId;
  private final LocalDate requestExpirationDate;
  private final LocalDate holdShelfExpirationDate;
  private final ItemSummary itemSummary;
  private final PatronSummary requesterSummary;
  private final PatronSummary proxySummary;
  private final String status;
  private final UUID cancellationReasonId;
  private final UUID cancelledByUserId;
  private final String cancellationAdditionalInformation;
  private final DateTime cancelledDate;

  public RequestRequestBuilder() {
    this(UUID.randomUUID(),
      "Hold",
      new DateTime(2017, 7, 15, 9, 35, 27, DateTimeZone.UTC),
      UUID.randomUUID(),
      UUID.randomUUID(),
      null,
      "Hold Shelf",
      null,
      null,
      null,
      null,
      null,
      null,
      "Open - Not yet filled",
      null,
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
    UUID proxyId,
    String fulfilmentPreference,
    UUID deliveryAddressTypeId,
    LocalDate requestExpirationDate,
    LocalDate holdShelfExpirationDate,
    ItemSummary itemSummary,
    PatronSummary requesterSummary,
    PatronSummary proxySummary,
    String status,
    UUID cancellationReasonId,
    UUID cancelledByUserId,
    String cancellationAdditionalInformation,
    DateTime cancelledDate) {

    this.id = id;
    this.requestType = requestType;
    this.requestDate = requestDate;
    this.itemId = itemId;
    this.requesterId = requesterId;
    this.proxyId = proxyId;
    this.fulfilmentPreference = fulfilmentPreference;
    this.deliveryAddressTypeId = deliveryAddressTypeId;
    this.requestExpirationDate = requestExpirationDate;
    this.holdShelfExpirationDate = holdShelfExpirationDate;
    this.itemSummary = itemSummary;
    this.requesterSummary = requesterSummary;
    this.proxySummary = proxySummary;
    this.status = status;
    this.cancellationReasonId = cancellationReasonId;
    this.cancelledByUserId = cancelledByUserId;
    this.cancellationAdditionalInformation = cancellationAdditionalInformation;
    this.cancelledDate = cancelledDate;
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

    if(status != null) {
      request.put("status", status);
    }

    if(proxyId != null) {
      request.put("proxyUserId", proxyId.toString());
    }

    if(deliveryAddressTypeId != null) {
      request.put("deliveryAddressTypeId", this.deliveryAddressTypeId.toString());
    }

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

    if(requesterSummary != null) {
      JsonObject requester = new JsonObject()
        .put("lastName", requesterSummary.lastName)
        .put("firstName", requesterSummary.firstName);

      if(requesterSummary.middleName != null) {
        requester.put("middleName", requesterSummary.middleName);
      }

      requester.put("barcode", requesterSummary.barcode);

      request.put("requester", requester);
    }

    if(proxySummary != null) {
      JsonObject proxy = new JsonObject()
        .put("lastName", proxySummary.lastName)
        .put("firstName", proxySummary.firstName);

      if(proxySummary.middleName != null) {
        proxy.put("middleName", proxySummary.middleName);
      }

      proxy.put("barcode", proxySummary.barcode);

      request.put("proxy", proxy);
    }

    return request;
  }

  public RequestRequestBuilder withRequestType(String requestType) {
    return new RequestRequestBuilder(
      this.id,
      requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder recall() {
    return withRequestType("Recall");
  }

  public RequestRequestBuilder hold() {
    return withRequestType("Hold");
  }

  public RequestRequestBuilder withId(UUID newId) {
    return new RequestRequestBuilder(
      newId,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withNoId() {
    return new RequestRequestBuilder(
      null,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withRequestDate(DateTime requestDate) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withItemId(UUID itemId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withRequesterId(UUID requesterId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder toHoldShelf() {
    return withFulfilmentPreference("Hold Shelf");
  }

  public RequestRequestBuilder deliverToAddress(UUID addressTypeId) {
    return withFulfilmentPreference("Delivery")
      .withDeliveryAddressType(addressTypeId);
  }

  public RequestRequestBuilder withFulfilmentPreference(String fulfilmentPreference) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withRequestExpiration(LocalDate requestExpiration) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      requestExpiration,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withHoldShelfExpiration(LocalDate holdShelfExpiration) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      holdShelfExpiration,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withItem(String title, String barcode) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      new ItemSummary(title, barcode),
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withRequester(
    String lastName,
    String firstName,
    String middleName,
    String barcode) {

    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      new PatronSummary(lastName, firstName, middleName, barcode),
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withRequester(
    String lastName,
    String firstName,
    String barcode) {

    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      new PatronSummary(lastName, firstName, null, barcode),
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withProxy(
    String lastName,
    String firstName,
    String barcode) {

    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      new PatronSummary(lastName, firstName, null, barcode),
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withDeliveryAddressType(UUID deliverAddressType) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      deliverAddressType,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withStatus(String status) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);

  }

  private String formatDateTime(DateTime requestDate) {
    return requestDate.toString(ISODateTimeFormat.dateTime());
  }

  private String formatDateOnly(LocalDate date) {
    return date.toString(DateTimeFormat.forPattern("yyyy-MM-dd"));
  }

  public RequestRequestBuilder withProxyId(UUID proxyId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }
  
  public RequestRequestBuilder withCancellationReasonId(UUID cancellationReasonId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }
  
  public RequestRequestBuilder withCancelledByUserId(UUID cancelledByUserId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate);
  }

  public RequestRequestBuilder withCancellationAdditionalInformation(String cancellationAdditionalInformation) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      cancellationAdditionalInformation,
      this.cancelledDate);
  }
    
  public RequestRequestBuilder withCancelledDate(DateTime cancelledDate) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.proxyId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      cancelledDate);
  }
  
  private class ItemSummary {
    public final String title;
    public final String barcode;

    public ItemSummary(String title, String barcode) {
      this.title = title;
      this.barcode = barcode;
    }
  }

  private class PatronSummary {
    public final String lastName;
    public final String firstName;
    public final String middleName;
    public final String barcode;

    public PatronSummary(String lastName, String firstName, String middleName, String barcode) {
      this.lastName = lastName;
      this.firstName = firstName;
      this.middleName = middleName;
      this.barcode = barcode;
    }
  }
}
