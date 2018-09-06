package org.folio.rest.support;

import java.util.Collection;
import java.util.List;

import io.vertx.core.json.JsonObject;

public class MultipleRecords<T> {
  private static final String TOTAL_RECORDS_PROPERTY_NAME = "totalRecords";

  private final Collection<T> records;
  private final Integer totalRecords;

  private MultipleRecords(Collection<T> records, Integer totalRecords) {
    this.records = records;
    this.totalRecords = totalRecords;
  }

  public static MultipleRecords<JsonObject> fromJson(
    JsonObject wrapper,
    String collectionPropertyName) {

    List<JsonObject> wrappedRecords = JsonArrayHelper.toList(wrapper,
      collectionPropertyName);

    Integer totalRecords = wrapper.getInteger(TOTAL_RECORDS_PROPERTY_NAME);

    return new MultipleRecords<>(
      wrappedRecords, totalRecords);
  }

  public Collection<T> getRecords() {
    return records;
  }

  public Integer getTotalRecords() {
    return totalRecords;
  }
}
