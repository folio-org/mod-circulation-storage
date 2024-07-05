package org.folio.rest.model;

import lombok.Data;
import org.folio.rest.jaxrs.model.Metadata;
import java.util.Date;

@Data
public class PrintEvent {
  private String id;
  private String requestId;
  private String requesterId;
  private String requesterName;
  private Date printEventDate;
  private Metadata metadata;
}
