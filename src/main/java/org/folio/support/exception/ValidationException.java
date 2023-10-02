package org.folio.support.exception;

import java.util.List;

import org.folio.rest.jaxrs.model.Error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ValidationException extends RuntimeException {
  private final transient List<Error> errors;

  public ValidationException(Error error) {
    this(List.of(error));
  }
}
