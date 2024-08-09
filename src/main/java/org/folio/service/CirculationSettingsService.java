package org.folio.service;

import static org.folio.rest.tools.utils.ValidationHelper.isDuplicate;
import static org.folio.service.event.EntityChangedEventPublisherFactory.circulationSettingsEventPublisher;
import static org.folio.support.ModuleConstants.CIRCULATION_SETTINGS_TABLE;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.persist.CirculationSettingsRepository;
import org.folio.rest.jaxrs.model.CirculationSetting;
import org.folio.rest.jaxrs.model.CirculationSettings;
import org.folio.rest.jaxrs.resource.CirculationSettingsStorage.DeleteCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse;
import org.folio.rest.jaxrs.resource.CirculationSettingsStorage.GetCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse;
import org.folio.rest.jaxrs.resource.CirculationSettingsStorage.GetCirculationSettingsStorageCirculationSettingsResponse;
import org.folio.rest.jaxrs.resource.CirculationSettingsStorage.PutCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.service.event.EntityChangedEventPublisher;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class CirculationSettingsService {

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
      GetCirculationSettingsStorageCirculationSettingsResponse.class);
  }

  public Future<CirculationSetting> create(CirculationSetting circulationSetting) {
    return repository.save(circulationSetting.getId(), circulationSetting)
      .map(circulationSetting)
      .recover(throwable -> updateSettingsValue(circulationSetting, throwable));
  }

  public Future<Response> findById(String circulationSettingsId) {
    return PgUtil.getById(CIRCULATION_SETTINGS_TABLE, CirculationSetting.class,
      circulationSettingsId, okapiHeaders, vertxContext,
      GetCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse.class);
  }

  public Future<Response> update(String circulationSettingsId, CirculationSetting circulationSetting) {
    return PgUtil.put(CIRCULATION_SETTINGS_TABLE, circulationSetting, circulationSettingsId, okapiHeaders, vertxContext,
        PutCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse.class)
      .compose(eventPublisher.publishUpdated(circulationSetting));
  }

  public Future<Response> delete(String circulationSettingsId) {
    return repository.getById(circulationSettingsId).compose (
      circulationSetting -> PgUtil.deleteById(CIRCULATION_SETTINGS_TABLE, circulationSettingsId, okapiHeaders, vertxContext,
        DeleteCirculationSettingsStorageCirculationSettingsByCirculationSettingsIdResponse.class)
      .compose(eventPublisher.publishRemoved(circulationSetting))
    );
  }

  private Future<CirculationSetting> updateSettingsValue(CirculationSetting circulationSetting,
                                                         Throwable throwable) {
    if (!isDuplicate(throwable.getMessage())) {
      return Future.failedFuture(throwable);
    }

    return getSettingsByName(circulationSetting.getName())
      .compose(settings -> updateSettings(settings, circulationSetting));
  }

  private Future<CirculationSetting> updateSettings(List<CirculationSetting> settings,
                                                    CirculationSetting circulationSetting) {
    settings.forEach(setting -> setting.setValue(circulationSetting.getValue()));
    return repository.update(settings)
      .map(circulationSetting);
  }

  private Future<List<CirculationSetting>> getSettingsByName(String settingsName) {
    Criterion filter = new Criterion(new Criteria()
      .addField("'name'")
      .setOperation("=")
      .setVal(settingsName));

    return repository.get(filter);
  }
}
