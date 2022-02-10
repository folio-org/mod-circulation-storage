package org.folio.service.checkin;

import static org.folio.service.event.EntityChangedEventPublisherFactory.checkInEventPublisher;
import static org.folio.support.ModuleConstants.CHECKIN_CLASS;
import static org.folio.support.ModuleConstants.CHECKIN_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.CheckIn;
import org.folio.rest.jaxrs.model.CheckIns;
import org.folio.rest.jaxrs.resource.CheckInStorageCheckIns;
import org.folio.rest.persist.PgUtil;
import org.folio.service.event.EntityChangedEventPublisher;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class CheckInService {

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final EntityChangedEventPublisher<String, CheckIn> eventPublisher;

  public CheckInService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    this.eventPublisher = checkInEventPublisher(vertxContext, okapiHeaders);
  }

  public Future<Response> findByQuery(String query, int offset, int limit) {
    return PgUtil.get(CHECKIN_TABLE, CHECKIN_CLASS, CheckIns.class, query, offset, limit, okapiHeaders, vertxContext,
        CheckInStorageCheckIns.GetCheckInStorageCheckInsResponse.class);
  }

  public Future<Response> findById(String checkInId) {
    return PgUtil.getById(CHECKIN_TABLE, CHECKIN_CLASS, checkInId, okapiHeaders, vertxContext,
        CheckInStorageCheckIns.GetCheckInStorageCheckInsByCheckInIdResponse.class);
  }

  public Future<Response> create(CheckIn entity) {
    Promise<Response> createResult = Promise.promise();

    PgUtil.post(CHECKIN_TABLE, entity, okapiHeaders, vertxContext,
        CheckInStorageCheckIns.PostCheckInStorageCheckInsResponse.class, createResult);

    return createResult.future()
        .compose(eventPublisher.publishCreated());
  }

}
