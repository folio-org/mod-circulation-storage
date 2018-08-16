package org.folio.rest.impl.support;

import io.vertx.core.logging.Logger;

public class LogWriter {
  private final Logger logger;

  public LogWriter(Logger logger) {
    this.logger = logger;
  }

  public void error(Throwable cause) {
    logger.error(cause.getMessage(), cause);
  }
}
