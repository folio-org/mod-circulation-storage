package org.folio.rest.support.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = NoticeConfigDto.NoticeConfigDtoBuilder.class)
@EqualsAndHashCode
public class NoticeConfigDto {
  String timing;
  String templateId;
  String format;
  boolean sendInRealTime;

  @JsonPOJOBuilder(withPrefix = "")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class NoticeConfigDtoBuilder {}
}
