package org.folio.rest.support.clients;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public final class OkapiHeaders {
  private final String url;
  private final String tenantId;
  private final String token;
  private final String userId;
}
