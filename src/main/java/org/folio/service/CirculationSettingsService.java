package org.folio.service;

import static org.folio.service.event.EntityChangedEventPublisherFactory.circulationSettingsEventPublisher;
import static org.folio.support.ModuleConstants.CIRCULATION_SETTINGS_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.CirculationSettingsRepository;
import org.folio.rest.jaxrs.model.CirculationSetting;
import org.folio.rest.jaxrs.model.CirculationSettings;
import org.folio.rest.jaxrs.resource.CirculationSettingsStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.service.event.EntityChangedEventPublisher;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class CirculationSettingsService {

  private static final Logger log = LogManager.getLogger(CirculationSettingsService.class);
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final CirculationSettingsRepository repository;
  private final EntityChangedEventPublisher<String, CirculationSetting> eventPublisher;

  public CirculationSettingsService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    this.repository = new CirculationSettingsRepository(vertxContext, okapiHeaders);
    this.eventPublisher = circulationSettingsEventPublisher(vertxContext, okapiHeaders);
  }

  public Future<Response> getAll(int offset, int limit, String query) {
    return PgUtil.get(CIRCULATION_SETTINGS_TABLE, CirculationSetting.class,
      CirculationSettings.class,
      query, offset, limit, okapiHeaders, vertxContext,
      CirculationSettingsStorage.GetCirculationSettingsStorageCirculationSettingsResponse.class);
  }

  public Future<Response> create(CirculationSetting circulationSetting) {
    Promise<Response> createResult = Promise.promise();
    PgUtil.post(CIRCULATION_SETTINGS_TABLE, circulationSetting, okapiHeaders, vertxContext,
      CirculationSettingsStorage.PostCirculationSettingsStorageCirculationSettingsResponse.class, createResult);
    return createResult.future()
      .compose(eventPublisher.publishCreated());
  }

  public Future<Response> findById(String circulationSettingsId) {
    return PgUtil.getById(CIRCULATION_SETTINGS_TABLE, CirculationSetting.class,
      circulationSettingsId, okapiHeaders, vertxContext,
      CirculationSettingsStorage.GetCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse.class);
  }

  public Future<Response> update(String circulationSettingsId, CirculationSetting circulationSetting) {
    Promise<Response> updateResult = Promise.promise();
    PgUtil.put(CIRCULATION_SETTINGS_TABLE, circulationSetting, circulationSettingsId, okapiHeaders, vertxContext,
      CirculationSettingsStorage.PutCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse.class, updateResult);
    return updateResult.future()
      .compose(eventPublisher.publishUpdated(circulationSetting));
  }

  public Future<Response> delete(String circulationSettingsId) {
    return repository.getById(circulationSettingsId).compose ( circulationSetting -> {
      Promise<Response> deleteResult = Promise.promise();
      PgUtil.deleteById(CIRCULATION_SETTINGS_TABLE, circulationSettingsId, okapiHeaders, vertxContext,
        CirculationSettingsStorage.DeleteCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse.class, deleteResult);
      return deleteResult.future()
        .compose(eventPublisher.publishRemoved(circulationSetting));
      }
    );
  }
}
