package org.folio.rest.support.builders;

import java.util.UUID;

import org.folio.rest.jaxrs.model.CheckIn;
import org.joda.time.DateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.vertx.core.json.JsonObject;

public class CheckInBuilder implements Builder {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  private final CheckIn delegate;

  public CheckInBuilder() {
    this.delegate = new CheckIn();
  }

  public CheckInBuilder withId(UUID id) {
    if (id != null) {
      delegate.withId(id.toString());
    }

    return this;
  }

  public CheckInBuilder withOccurredDateTime(DateTime occurredDateTime) {
    delegate.withOccurredDateTime(occurredDateTime.toDate());

    return this;
  }

  public CheckInBuilder withItemId(UUID itemId) {
    delegate.withItemId(itemId.toString());

    return this;
  }

  public CheckInBuilder withServicePointId(UUID checkInServicePointId) {
    delegate.withServicePointId(checkInServicePointId.toString());

    return this;
  }

  public CheckInBuilder withPerformedByUserId(UUID performedByUserId) {
    delegate.withPerformedByUserId(performedByUserId.toString());

    return this;
  }

  public CheckInBuilder withItemStatusPriorToCheckIn(String itemStatus) {
    delegate.withItemStatusPriorToCheckIn(itemStatus);

    return this;
  }

  public CheckInBuilder withRequestQueueSize(Integer requestQueueSize) {
    delegate.withRequestQueueSize(requestQueueSize);

    return this;
  }

  public CheckInBuilder withItemLocationId(UUID itemLocationId) {
    delegate.withItemLocationId(itemLocationId != null
      ? itemLocationId.toString() : null);

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
