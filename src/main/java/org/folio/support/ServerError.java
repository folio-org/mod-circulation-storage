package org.folio.support;

import io.vertx.core.impl.NoStackTraceThrowable;

class ServerError {
  boolean isUnknown(Throwable error) {
    //When a failed vert.x future received a null cause,
    // it is replaced by an instance of NoStackTraceThrowable
    return error == null || error instanceof NoStackTraceThrowable;
  }
}
