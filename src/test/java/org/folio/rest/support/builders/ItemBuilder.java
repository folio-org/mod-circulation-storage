package org.folio.rest.support.builders;

import static java.util.UUID.randomUUID;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
public class ItemBuilder {
  private final String id;
  private final String barcode;
  private final String status;
  private final String materialTypeId;
  private final String permanentLoanTypeId;
  private final String holdingsRecordId;
  private final String effectiveShelvingOrder;
  private final ItemCallNumberComponents callNumberComponents;

  public ItemBuilder() {
    this(
      randomUUID().toString(),
      "default-barcode",
      "Available",
      randomUUID().toString(),
      randomUUID().toString(),
      randomUUID().toString(),
      null,
      null
    );
  }

  public JsonObject create() {
    JsonObject item = new JsonObject()
      .put("id", id)
      .put("barcode", barcode)
      .put("materialTypeId", materialTypeId)
      .put("permanentLoanTypeId", permanentLoanTypeId)
      .put("holdingsRecordId", holdingsRecordId)
      .put("effectiveShelvingOrder", effectiveShelvingOrder)
      .put("status", new JsonObject().put("name", status));

    if (callNumberComponents != null) {
      item.put("effectiveCallNumberComponents", new JsonObject()
        .put("callNumber", callNumberComponents.getCallNumber())
        .put("prefix", callNumberComponents.getPrefix())
        .put("suffix", callNumberComponents.getSuffix()));
    }

    return item;
  }

  @Getter
  @With
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ItemCallNumberComponents {
    private String callNumber;
    private String prefix;
    private String suffix;
  }

}
