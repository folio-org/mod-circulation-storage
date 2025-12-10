package org.folio.rest.support;

import static org.folio.rest.support.ApiTests.waitFor;

import java.util.UUID;

import org.folio.rest.jaxrs.model.CirculationSetting;
import org.folio.rest.jaxrs.model.Value;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class CirculationSettingsHelper {

  private static final String CIRCULATION_SETTINGS_TABLE = "circulation_settings";
  private static final Criterion GENERAL_TLR_SETTING_CRITERION = new Criterion(new Criteria()
    .addField("'name'")
    .setOperation("=")
    .setVal("generalTlr"));

  private final PostgresClient pgClient;

  public void changeTlrSettings(boolean isTlrEnabled) {
    changeTlrSettings(isTlrEnabled, false, false);
  }

  @SneakyThrows
  public void changeTlrSettings(boolean titleLevelRequestsFeatureEnabled,
    boolean createTitleLevelRequestsByDefault, boolean tlrHoldShouldFollowCirculationRules) {

    changeTlrSettings(new Value()
      .withAdditionalProperty("titleLevelRequestsFeatureEnabled", titleLevelRequestsFeatureEnabled)
      .withAdditionalProperty("createTitleLevelRequestsByDefault", createTitleLevelRequestsByDefault)
      .withAdditionalProperty("tlrHoldShouldFollowCirculationRules", tlrHoldShouldFollowCirculationRules));
  }

  public void changeTlrSettings(Value newValue) {
    waitFor(
      pgClient.get(CIRCULATION_SETTINGS_TABLE, CirculationSetting.class, GENERAL_TLR_SETTING_CRITERION)
        .compose(settings -> settings.getResults().isEmpty()
          ? createTlrSettings(newValue)
          : updateTlrSettings(settings.getResults().getFirst().withValue(newValue))
    ));
  }

  private Future<Void> createTlrSettings(Value newSettingValue) {
    CirculationSetting newTlrSettings = new CirculationSetting()
      .withId(UUID.randomUUID().toString())
      .withName("generalTlr")
      .withValue(newSettingValue);

    return pgClient.save(CIRCULATION_SETTINGS_TABLE, newTlrSettings)
      .mapEmpty();
  }

  private Future<Void> updateTlrSettings(CirculationSetting settings) {
    return pgClient.update(CIRCULATION_SETTINGS_TABLE, settings, settings.getId())
      .mapEmpty();
  }

  public void removeTlrSettings() {
    waitFor(pgClient.delete(CIRCULATION_SETTINGS_TABLE, GENERAL_TLR_SETTING_CRITERION));
  }

}
