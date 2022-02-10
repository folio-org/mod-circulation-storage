package org.folio.service.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class EntityChangedData<E> {

  @JsonProperty("old")
  E oldEntity;
  @JsonProperty("new")
  E newEntity;

}