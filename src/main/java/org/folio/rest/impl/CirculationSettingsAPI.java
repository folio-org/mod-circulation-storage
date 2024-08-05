package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.CirculationSetting;
import org.folio.rest.jaxrs.resource.CirculationSettingsStorage;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.service.CirculationSettingsService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.CirculationSettingsStorage.PostCirculationSettingsStorageCirculationSettingsResponse.headersFor201;
import static org.folio.rest.jaxrs.resource.CirculationSettingsStorage.PostCirculationSettingsStorageCirculationSettingsResponse.respond201WithApplicationJson;
import static org.folio.rest.jaxrs.resource.CirculationSettingsStorage.PostCirculationSettingsStorageCirculationSettingsResponse.respond500WithTextPlain;

public class CirculationSettingsAPI implements CirculationSettingsStorage {

  @Override
  public void postCirculationSettingsStorageCirculationSettings(String lang,
                                                                CirculationSetting circulationSettings, Map<String, String> okapiHeaders,
                                                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new CirculationSettingsService(vertxContext, okapiHeaders)
      .create(circulationSettings)
      .onSuccess(reply -> asyncResultHandler.handle(succeededFuture(respond201WithApplicationJson(circulationSettings, headersFor201()))))
      .onFailure(errorReply -> {
        if(ValidationHelper.isDuplicate(errorReply.getMessage())) {
          asyncResultHandler.handle(succeededFuture(respond201WithApplicationJson(circulationSettings, headersFor201())));
        } else {
          asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(errorReply.getMessage())));
        }
      });
  }

  @Override
  public void getCirculationSettingsStorageCirculationSettings(int offset,
    int limit, String query, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new CirculationSettingsService(vertxContext, okapiHeaders)
      .getAll(offset, limit, query)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void getCirculationSettingsStorageCirculationSettingsByCirculationSettingsId(
    String circulationSettingsId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

      new CirculationSettingsService(vertxContext, okapiHeaders)
        .findById(circulationSettingsId)
        .onComplete(asyncResultHandler);
  }

  @Override
  public void putCirculationSettingsStorageCirculationSettingsByCirculationSettingsId(
    String circulationSettingsId, String lang, CirculationSetting entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

      new CirculationSettingsService(vertxContext, okapiHeaders)
        .update(circulationSettingsId, entity)
        .onComplete(asyncResultHandler);
  }

  @Override
  public void deleteCirculationSettingsStorageCirculationSettingsByCirculationSettingsId(
    String circulationSettingsId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

      new CirculationSettingsService(vertxContext, okapiHeaders)
        .delete(circulationSettingsId)
        .onComplete(asyncResultHandler);
  }
}
