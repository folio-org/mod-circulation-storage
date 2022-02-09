package org.folio.support;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.support.ResponseUtil.badRequestResponse;
import static org.folio.support.ResponseUtil.isCreateSuccessResponse;
import static org.folio.support.ResponseUtil.noContentResponse;

import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.persist.AbstractRepository;
import org.folio.service.event.EntityChangedEventPublisher;

import io.vertx.core.Future;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;

public class ServiceHelper<T> {

  private final AbstractRepository<T> repository;
  private final EntityChangedEventPublisher<String, T> eventPublisher;

  public ServiceHelper(AbstractRepository<T> repository,
      EntityChangedEventPublisher<String, T> eventPublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
  }

  public Future<Response> upsertAndPublishEvents(String id, T rec) {
    return repository.getById(id)
        .compose(
            oldRecord -> upsert(id, rec, oldRecord)
                .compose(publishCreatedOrUpdatedEvent(oldRecord))
                .compose(toNoContentResponse())
        );
  }

  public String jsonStringOrEmpty(Object obj) {
    if (obj == null) {
      return StringUtils.EMPTY;
    }

    String result;
    try {
      result = Json.encode(obj);
    } catch (EncodeException e) {
      result = StringUtils.EMPTY;
    }

    return result;
  }

  private Future<Response> upsert(String id, T rec, T oldRecord) {
    return repository.upsert(id, rec)
        .compose(
            upsertId -> toCreatedOrUpdatedResponse(upsertId, oldRecord),
            err -> succeededFuture(badRequestResponse(err)) // upsert failure is treated as BAD REQUEST in MyPgUtil.putUpsert204()
        );
  }

  private Future<Response> toCreatedOrUpdatedResponse(String id, T oldRecord) {
    return oldRecord == null
        ? repository.getById(id).map(ResponseUtil::createdResponse)
        : succeededFuture(noContentResponse());
  }

  private Function<Response, Future<Response>> publishCreatedOrUpdatedEvent(T oldRecord) {
    return oldRecord == null
        ? eventPublisher.publishCreated()
        : eventPublisher.publishUpdated(oldRecord);
  }

  private Function<Response, Future<Response>> toNoContentResponse() {
    return response -> succeededFuture(
        isCreateSuccessResponse(response) // transform CREATED to NO_CONTENT (see MyPgUtil.putUpsert204() )
            ? noContentResponse()
            : response
    );
  }

}
