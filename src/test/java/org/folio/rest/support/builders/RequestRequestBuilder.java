package org.folio.rest.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Tags;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.UUID;

public class RequestRequestBuilder extends JsonBuilder {
  public static final String OPEN_NOT_YET_FILLED = "Open - Not yet filled";
  public static final String OPEN_AWAITING_PICKUP = "Open - Awaiting pickup";
  public static final String OPEN_IN_TRANSIT = "Open - In transit";
  public static final String CLOSED_FILLED = "Closed - Filled";
  public static final String CLOSED_CANCELLED = "Closed - Cancelled";
  public static final String CLOSED_UNFILLED = "Closed - Unfilled";
  public static final String CLOSED_PICKUP_EXPIRED = "Closed - Pickup expired";

  private final UUID id;
  private final String requestType;
  private final DateTime requestDate;
  private final UUID itemId;
  private final UUID requesterId;
  private final UUID proxyId;
  private final String fulfilmentPreference;
  private final UUID deliveryAddressTypeId;
  private final DateTime requestExpirationDate;
  private final DateTime holdShelfExpirationDate;
  private final ItemSummary itemSummary;
  private final PatronSummary requesterSummary;
  private final PatronSummary proxySummary;
  private final String status;
  private final UUID cancellationReasonId;
  private final UUID cancelledByUserId;
  private final String cancellationAdditionalInformation;
  private final DateTime cancelledDate;
  private final Integer position;
  private final UUID pickupServicePointId;
  private final Tags tags;

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
      null,
      1,
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
    DateTime requestExpirationDate,
    DateTime holdShelfExpirationDate,
    ItemSummary itemSummary,
    PatronSummary requesterSummary,
    PatronSummary proxySummary,
    String status,
    UUID cancellationReasonId,
    UUID cancelledByUserId,
    String cancellationAdditionalInformation,
    DateTime cancelledDate,
    Integer position,
    UUID pickupServicePointId,
    Tags tags) {

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
    this.position = position;
    this.pickupServicePointId = pickupServicePointId;
    this.tags = tags;
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "id", this.id);
    put(request, "requestType", this.requestType);
    put(request, "requestDate", this.requestDate);
    put(request, "itemId", this.itemId);
    put(request, "requesterId", this.requesterId);
    put(request, "fulfilmentPreference", this.fulfilmentPreference);
    put(request, "position", this.position);
    put(request, "status", status);
    put(request, "proxyUserId", proxyId);
    put(request, "deliveryAddressTypeId", this.deliveryAddressTypeId);
    put(request, "requestExpirationDate", this.requestExpirationDate);
    put(request, "holdShelfExpirationDate", this.holdShelfExpirationDate);
    put(request, "pickupServicePointId", this.pickupServicePointId);

    if(this.itemSummary != null) {
      final JsonObject item = new JsonObject();

      put(item, "title", this.itemSummary.title);
      put(item, "barcode", this.itemSummary.barcode);

      put(request, "item", item);
    }

    if(requesterSummary != null) {
      JsonObject requester = new JsonObject();

      put(requester, "lastName", requesterSummary.lastName);
      put(requester, "firstName", requesterSummary.firstName);
      put(requester, "middleName", requesterSummary.middleName);
      put(requester, "barcode", requesterSummary.barcode);

      put(request, "requester", requester);
    }

    if(proxySummary != null) {
      JsonObject proxy = new JsonObject();

      put(proxy, "lastName", proxySummary.lastName);
      put(proxy, "firstName", proxySummary.firstName);
      put(proxy, "middleName", proxySummary.middleName);
      put(proxy, "barcode", proxySummary.barcode);

      put(request, "proxy", proxy);
    }

    put(request, "cancellationReasonId", this.cancellationReasonId);
    put(request, "cancelledByUserId", this.cancelledByUserId);
    put(request, "cancellationAdditionalInformation",
      this.cancellationAdditionalInformation);

    put(request, "cancelledDate", this.cancelledDate);

    if (tags != null) {
      JsonObject tags = new JsonObject();
      tags.put("tagList", this.tags.getTagList());

      put(request, "tags", tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestRequestBuilder withRequestExpiration(DateTime requestExpiration) {
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestRequestBuilder withHoldShelfExpiration(DateTime holdShelfExpiration) {
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestRequestBuilder withPosition(Integer newPosition) {
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
      this.cancelledDate,
      newPosition,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestRequestBuilder withNoPosition() {
    return withPosition(null);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestRequestBuilder withPickupServicePointId(UUID pickupServicePointId) {
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
      this.cancelledDate,
      this.position,
      pickupServicePointId,
      this.tags);
  }

  public RequestRequestBuilder withTags(Tags tags) {
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
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      tags);
  }

  private class ItemSummary {
    final String title;
    final String barcode;

    ItemSummary(String title, String barcode) {
      this.title = title;
      this.barcode = barcode;
    }
  }

  private class PatronSummary {
    final String lastName;
    final String firstName;
    final String middleName;
    final String barcode;

    PatronSummary(
      String lastName,
      String firstName,
      String middleName,
      String barcode) {

      this.lastName = lastName;
      this.firstName = firstName;
      this.middleName = middleName;
      this.barcode = barcode;
    }
  }
}
