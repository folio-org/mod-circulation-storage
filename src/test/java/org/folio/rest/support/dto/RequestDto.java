package org.folio.rest.support.dto;

import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = RequestDto.RequestDtoBuilder.class)
public class RequestDto {
  @Builder.Default
  String status = "Open - Not yet filled";
  @Builder.Default
  String fulfilmentPreference = "Hold Shelf";
  String requesterId;
  String itemId;
  String holdingsRecordId;
  String instanceId;
  String requestType;
  String requestLevel;
  @Builder.Default
  Date requestDate = now(UTC).toDate();
  String pickupServicePointId;

  @JsonPOJOBuilder(withPrefix = "")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class RequestDtoBuilder {}
}
