package org.folio.rest.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;
import org.folio.rest.jaxrs.model.SearchIndex;
import org.folio.rest.jaxrs.model.Tags;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
@With
public class RequestRequestBuilder extends JsonBuilder {
  public static final String OPEN_NOT_YET_FILLED = "Open - Not yet filled";
  public static final String OPEN_AWAITING_PICKUP = "Open - Awaiting pickup";
  public static final String OPEN_IN_TRANSIT = "Open - In transit";
  public static final String OPEN_AWAITING_DELIVERY = "Open - Awaiting delivery";
  public static final String CLOSED_FILLED = "Closed - Filled";
  public static final String CLOSED_CANCELLED = "Closed - Cancelled";
  public static final String CLOSED_UNFILLED = "Closed - Unfilled";
  public static final String CLOSED_PICKUP_EXPIRED = "Closed - Pickup expired";

  private final UUID id;
  private final String requestType;
  private final String requestLevel;
  private final DateTime requestDate;
  private final UUID itemId;
  private final UUID instanceId;
  private final UUID requesterId;
  private final UUID proxyId;
  private final String fulfillmentPreference;
  private final UUID deliveryAddressTypeId;
  private final DateTime requestExpirationDate;
  private final DateTime holdShelfExpirationDate;
  private final RequestItemSummary itemSummary;
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
  private final String patronComments;
  private final UUID holdingsRecordId;
  private final SearchIndex searchIndex;
  private final String itemLocationCode;
  private final String ecsRequestPhase;

  public RequestRequestBuilder() {
    this(UUID.randomUUID(),
      "Hold",
      "Item",
      new DateTime(2017, 7, 15, 9, 35, 27, DateTimeZone.UTC),
      UUID.randomUUID(),
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
      null,
      null,
      UUID.randomUUID(),
      null,
      null,
      null);
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "id", this.id);
    put(request, "holdingsRecordId", this.holdingsRecordId);
    put(request, "requestType", this.requestType);
    put(request, "requestLevel", this.requestLevel);
    put(request, "requestDate", this.requestDate);
    put(request, "itemId", this.itemId);
    put(request, "instanceId", this.instanceId);
    put(request, "requesterId", this.requesterId);
    put(request, "fulfillmentPreference", this.fulfillmentPreference);
    put(request, "position", this.position);
    put(request, "status", status);
    put(request, "proxyUserId", proxyId);
    put(request, "deliveryAddressTypeId", this.deliveryAddressTypeId);
    put(request, "requestExpirationDate", this.requestExpirationDate);
    put(request, "holdShelfExpirationDate", this.holdShelfExpirationDate);
    put(request, "pickupServicePointId", this.pickupServicePointId);
    put(request, "ecsRequestPhase", this.ecsRequestPhase);

    if (this.itemSummary != null) {
      final JsonObject item = new JsonObject();
      put(item, "barcode", this.itemSummary.barcode);

      put(item, "itemEffectiveLocationId", this.itemSummary.itemEffectiveLocationId);
      put(item, "itemEffectiveLocationName", this.itemSummary.itemEffectiveLocationName);
      put(item, "retrievalServicePointId", this.itemSummary.retrievalServicePointId);
      put(item, "retrievalServicePointName", this.itemSummary.retrievalServicePointName);

      final JsonArray identifiers = new JsonArray(this.itemSummary.identifiers
        .stream()
        .map(pair -> new JsonObject()
          .put("identifierTypeId", pair.getKey().toString())
          .put("value", pair.getValue())
        ).collect(Collectors.toList()));

      final JsonObject instance = new JsonObject();
      put(instance, "title", this.itemSummary.title);
      instance.put("identifiers", identifiers);

      put(request, "item", item);
      put(request, "instance", instance);
    }

    if (requesterSummary != null) {
      JsonObject requester = new JsonObject();

      put(requester, "lastName", requesterSummary.lastName);
      put(requester, "firstName", requesterSummary.firstName);
      put(requester, "middleName", requesterSummary.middleName);
      put(requester, "barcode", requesterSummary.barcode);

      put(request, "requester", requester);
    }

    if (proxySummary != null) {
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
    put(request, "patronComments", this.patronComments);

    if (tags != null) {
      JsonObject tags = new JsonObject();
      tags.put("tagList", this.tags.getTagList());

      put(request, "tags", tags);
    }

    if (searchIndex != null) {
      put(request, "searchIndex", JsonObject.mapFrom(searchIndex));
    }

    if (itemLocationCode != null) {
      put(request, "itemLocationCode", this.itemLocationCode);
    }

    return request;
  }

  public RequestRequestBuilder recall() {
    return withRequestType("Recall");
  }

  public RequestRequestBuilder hold() {
    return withRequestType("Hold");
  }

  public RequestRequestBuilder page() {
    return withRequestType("Page");
  }

  public RequestRequestBuilder withNoId() {
    return new RequestRequestBuilder(
      null,
      this.requestType,
      this.requestLevel,
      this.requestDate,
      this.itemId,
      this.instanceId,
      this.requesterId,
      this.proxyId,
      this.fulfillmentPreference,
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
      this.tags,
      this.patronComments,
      this.holdingsRecordId,
      this.searchIndex,
      this.ecsRequestPhase,
      this.itemLocationCode);
  }

  public RequestRequestBuilder toHoldShelf() {
    return withFulfillmentPreference("Hold Shelf");
  }

  public RequestRequestBuilder deliverToAddress(UUID addressTypeId) {
    return withFulfillmentPreference("Delivery")
      .withDeliveryAddressTypeId(addressTypeId);
  }

  public RequestRequestBuilder withItem(String title, String barcode) {
    return withItem(new RequestItemSummary(title, barcode));
  }

  public RequestRequestBuilder withItem(RequestItemSummary item) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestLevel,
      this.requestDate,
      this.itemId,
      this.instanceId,
      this.requesterId,
      this.proxyId,
      this.fulfillmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      item,
      this.requesterSummary,
      this.proxySummary,
      this.status,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags,
      this.patronComments,
      this.holdingsRecordId,
      this.searchIndex,
      this.ecsRequestPhase,
      this.itemLocationCode);
  }

  public RequestRequestBuilder withRequester(
    String lastName,
    String firstName,
    String middleName,
    String barcode) {

    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestLevel,
      this.requestDate,
      this.itemId,
      this.instanceId,
      this.requesterId,
      this.proxyId,
      this.fulfillmentPreference,
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
      this.tags,
      this.patronComments,
      this.holdingsRecordId,
      this.searchIndex,
      this.ecsRequestPhase,
      this.itemLocationCode);
  }

  public RequestRequestBuilder withRequester(
    String lastName,
    String firstName,
    String barcode) {

    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestLevel,
      this.requestDate,
      this.itemId,
      this.instanceId,
      this.requesterId,
      this.proxyId,
      this.fulfillmentPreference,
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
      this.tags,
      this.patronComments,
      this.holdingsRecordId,
      this.searchIndex,
      this.ecsRequestPhase,
      this.itemLocationCode);
  }

  public RequestRequestBuilder withProxy(
    String lastName,
    String firstName,
    String barcode) {

    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestLevel,
      this.requestDate,
      this.itemId,
      this.instanceId,
      this.requesterId,
      this.proxyId,
      this.fulfillmentPreference,
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
      this.tags,
      this.patronComments,
      this.holdingsRecordId,
      this.searchIndex,
      this.ecsRequestPhase,
      this.itemLocationCode);
  }
  public RequestRequestBuilder withNoPosition() {
    return withPosition(null);
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

  public RequestRequestBuilder primary() {
    return withEcsRequestPhase("Primary");
  }

  public RequestRequestBuilder secondary() {
    return withEcsRequestPhase("Secondary");
  }

}
