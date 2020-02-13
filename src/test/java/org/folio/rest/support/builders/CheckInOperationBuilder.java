package org.folio.rest.support.builders;

import java.util.UUID;

import org.folio.rest.jaxrs.model.CheckInOperation;
import org.joda.time.DateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.vertx.core.json.JsonObject;

public class CheckInOperationBuilder implements Builder {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  private final CheckInOperation delegate;

  public CheckInOperationBuilder() {
    this.delegate = new CheckInOperation()
      .withId(UUID.randomUUID().toString());
  }

  public CheckInOperationBuilder withId(UUID id) {
    delegate.withId(id.toString());

    return this;
  }

  public CheckInOperationBuilder withOccurredDateTime(DateTime occurredDateTime) {
    delegate.withOccurredDateTime(occurredDateTime.toDate());

    return this;
  }

  public CheckInOperationBuilder withItemId(UUID itemId) {
    delegate.withItemId(itemId.toString());

    return this;
  }

  public CheckInOperationBuilder withItemStatus(String itemStatus) {
    delegate.withItemStatus(itemStatus);

    return this;
  }

  public CheckInOperationBuilder withCheckInServicePointId(UUID checkInServicePointId) {
    delegate.withCheckInServicePointId(checkInServicePointId.toString());

    return this;
  }

  public CheckInOperationBuilder withPerformedByUserId(UUID performedByUserId) {
    delegate.withPerformedByUserId(performedByUserId.toString());

    return this;
  }

  @Override
  public JsonObject create() {
    try {
      return new JsonObject(MAPPER.writeValueAsString(delegate));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
