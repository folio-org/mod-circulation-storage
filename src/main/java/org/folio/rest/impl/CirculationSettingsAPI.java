package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.CirculationSettingsStorage.PostCirculationSettingsStorageCirculationSettingsResponse.headersFor201;
import static org.folio.rest.jaxrs.resource.CirculationSettingsStorage.PostCirculationSettingsStorageCirculationSettingsResponse.respond201WithApplicationJson;
import static org.folio.rest.jaxrs.resource.CirculationSettingsStorage.PostCirculationSettingsStorageCirculationSettingsResponse.respond500WithTextPlain;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.CirculationSetting;
import org.folio.rest.jaxrs.resource.CirculationSettingsStorage;
import org.folio.service.CirculationSettingsService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class CirculationSettingsAPI implements CirculationSettingsStorage {

  @Override
  public void postCirculationSettingsStorageCirculationSettings(
    CirculationSetting circulationSettings, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new CirculationSettingsService(vertxContext, okapiHeaders)
      .create(circulationSettings)
      .onSuccess(response -> asyncResultHandler.handle(
        succeededFuture(respond201WithApplicationJson(circulationSettings, headersFor201()))))
      .onFailure(throwable -> asyncResultHandler.handle(
        succeededFuture(respond500WithTextPlain(throwable.getMessage()))));
  }

  @Override
  public void getCirculationSettingsStorageCirculationSettings(String totalRecords, int offset,
    int limit, String query, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new CirculationSettingsService(vertxContext, okapiHeaders)
      .getAll(offset, limit, query)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void getCirculationSettingsStorageCirculationSettingsByCirculationSettingsId(
    String circulationSettingsId, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

      new CirculationSettingsService(vertxContext, okapiHeaders)
        .findById(circulationSettingsId)
        .onComplete(asyncResultHandler);
  }

  @Override
  public void putCirculationSettingsStorageCirculationSettingsByCirculationSettingsId(
    String circulationSettingsId, CirculationSetting entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

      new CirculationSettingsService(vertxContext, okapiHeaders)
        .update(circulationSettingsId, entity)
        .onComplete(asyncResultHandler);
  }

  @Override
  public void deleteCirculationSettingsStorageCirculationSettingsByCirculationSettingsId(
    String circulationSettingsId, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

      new CirculationSettingsService(vertxContext, okapiHeaders)
        .delete(circulationSettingsId)
        .onComplete(asyncResultHandler);
  }
}
