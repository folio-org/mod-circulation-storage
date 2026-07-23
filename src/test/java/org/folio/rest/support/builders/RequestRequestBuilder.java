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
public class RequestRequestBuilder extends JsonBuilder implements Builder {
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

  public static RequestRequestBuilder from(JsonObject example) {
    final UUID id = example.containsKey("id")
        ? UUID.fromString(example.getString("id"))
        : null;

    final UUID holdingsRecordId = example.containsKey("holdingsRecordId")
        ? UUID.fromString(example.getString("holdingsRecordId"))
        : null;

    final DateTime requestDate = example.containsKey("requestDate")
        ? DateTime.parse(example.getString("requestDate"))
        : null;

    final UUID itemId = example.containsKey("itemId")
        ? UUID.fromString(example.getString("itemId"))
        : null;

    final UUID instanceId = example.containsKey("instanceId")
        ? UUID.fromString(example.getString("instanceId"))
        : null;

    final UUID requesterId = example.containsKey("requesterId")
        ? UUID.fromString(example.getString("requesterId"))
        : null;
    PatronSummary requesterSummary = null;

    if (example.containsKey("requester")) {
      JsonObject requester = example.getJsonObject("requester");

      requesterSummary = new PatronSummary(
          requester.getString("lastName"),
          requester.getString("firstName"),
          requester.getString("middleName"),
          requester.getString("barcode"));
    }

    final UUID proxyId = example.containsKey("proxyUserId")
        ? UUID.fromString(example.getString("proxyUserId"))
        : null;

    PatronSummary proxySummary = null;

    if (example.containsKey("proxy")) {
      JsonObject proxy = example.getJsonObject("proxy");

      proxySummary = new PatronSummary(
          proxy.getString("lastName"),
          proxy.getString("firstName"),
          proxy.getString("middleName"),
          proxy.getString("barcode"));
    }

    final UUID deliveryAddressTypeId = example.containsKey("deliveryAddressTypeId")
        ? UUID.fromString(example.getString("deliveryAddressTypeId"))
        : null;

    final DateTime requestExpirationDate = example.containsKey("requestExpirationDate")
        ? DateTime.parse(example.getString("requestExpirationDate"))
        : null;

    final DateTime holdShelfExpirationDate = example.containsKey("holdShelfExpirationDate")
        ? DateTime.parse(example.getString("holdShelfExpirationDate"))
        : null;

    final UUID cancellationReasonId = example.containsKey("cancellationReasonId")
        ? UUID.fromString(example.getString("cancellationReasonId"))
        : null;

    final UUID cancelledByUserId = example.containsKey("cancelledByUserId")
        ? UUID.fromString(example.getString("cancelledByUserId"))
        : null;

    final DateTime cancelledDate = example.containsKey("cancelledDate")
        ? DateTime.parse(example.getString("cancelledDate"))
        : null;

    final UUID pickupServicePointId = example.containsKey("pickupServicePointId")
        ? UUID.fromString(example.getString("pickupServicePointId"))
        : null;

    SearchIndex searchIndex = example.containsKey("searchIndex")
        ? example.getJsonObject("searchIndex").mapTo(SearchIndex.class)
        : null;

    return new RequestRequestBuilder(
        id,
        example.getString("requestType"),
        example.getString("requestLevel"),
        requestDate,
        itemId,
        instanceId,
        requesterId,
        proxyId,
        example.getString("fulfillmentPreference"),
        deliveryAddressTypeId,
        requestExpirationDate,
        holdShelfExpirationDate,
        null,
        requesterSummary,
        proxySummary,
        example.getString("status"),
        cancellationReasonId,
        cancelledByUserId,
        example.getString("cancellationAdditionalInformation"),
        cancelledDate,
        example.getInteger("position"),
        pickupServicePointId,
        null,
        example.getString("patronComments"),
        holdingsRecordId,
        searchIndex,
        example.getString("itemLocationCode"),
        example.getString("ecsRequestPhase"));
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

  public RequestRequestBuilder closed(String reason) {
    return withStatus(reason);
  }

  public RequestRequestBuilder closed() {
    return closed(CLOSED_FILLED);
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

  private static class PatronSummary {
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

  public RequestRequestBuilder intermediate() {
    return withEcsRequestPhase("Intermediate");
  }

}
