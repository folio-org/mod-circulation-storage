package org.folio.rest.support.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = ScheduledNoticeDto.ScheduledNoticeDtoBuilder.class)
@EqualsAndHashCode
public class ScheduledNoticeDto {
  Date nextRunTime;
  String triggeringEvent;
  NoticeConfigDto noticeConfig;
  String requestId;

  @JsonPOJOBuilder(withPrefix = "")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class ScheduledNoticeDtoBuilder {}
}
