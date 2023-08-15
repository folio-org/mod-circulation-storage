package org.folio.support.exception;

import java.util.List;

import org.folio.rest.jaxrs.model.Error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ValidationException extends RuntimeException {
  private transient final List<Error> errors;
}
