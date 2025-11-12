package org.folio.rest.api.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.rest.api.StorageTestSuite.cleanUpTable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.Map;

import org.folio.rest.jaxrs.model.CirculationSetting;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

public class CirculationSettingsMigrationScriptTest extends MigrationTestBase {
  private static final String CIRCULATION_SETTINGS_TABLE = "circulation_settings";

  private static final String CIRCULATION_SETTINGS_MIGRATION_SCRIPT_PATH =
    "/templates/db_scripts/migrate_circulation_settings.sql";
  private static final String TEST_SCRIPTS_PATH = "/db_scripts/circulation_settings_migration/";
  private static final String CREATE_MOD_CONFIGURATION_SCHEMA_SCRIPT_PATH =
    TEST_SCRIPTS_PATH + "create_mod_configuration_schema.sql";
  private static final String DROP_MOD_CONFIGURATION_SCHEMA_SCRIPT_PATH =
    TEST_SCRIPTS_PATH + "drop_mod_configuration_schema.sql";
  private static final String CREATE_MOD_SETTINGS_SCHEMA_SCRIPT_PATH =
    TEST_SCRIPTS_PATH + "create_mod_settings_schema.sql";
  private static final String DROP_MOD_SETTINGS_SCHEMA_SCRIPT_PATH =
    TEST_SCRIPTS_PATH + "drop_mod_settings_schema.sql";
  private static final String CREATE_CONFIGURATION_ENTRIES_SCRIPT_PATH =
    TEST_SCRIPTS_PATH + "create_configuration_entries.sql";
  private static final String CREATE_SETTINGS_ENTRIES_SCRIPT_PATH =
    TEST_SCRIPTS_PATH + "create_settings_entries.sql";

  @Before
  public void beforeEach() {
    cleanUpTable(CIRCULATION_SETTINGS_TABLE);
    executeSqlScript(DROP_MOD_CONFIGURATION_SCHEMA_SCRIPT_PATH);
    executeSqlScript(DROP_MOD_SETTINGS_SCHEMA_SCRIPT_PATH);
  }

  @Test
  public void nothingIsMigratedIfSourceSchemasDoNotExist() {
    executeSqlScript(CIRCULATION_SETTINGS_MIGRATION_SCRIPT_PATH);
    assertEquals(0, getCirculationSettings().size());
  }

  @Test
  public void nothingIsMigratedIfSourceTablesAreEmpty() {
    executeSqlScript(CREATE_MOD_CONFIGURATION_SCHEMA_SCRIPT_PATH);
    executeSqlScript(CREATE_MOD_SETTINGS_SCHEMA_SCRIPT_PATH);
    executeSqlScript(CIRCULATION_SETTINGS_MIGRATION_SCRIPT_PATH);
    assertEquals(0, getCirculationSettings().size());
  }

  @Test
  public void configurationEntriesFromModConfigurationAreMigrated() {
    executeSqlScript(CREATE_MOD_CONFIGURATION_SCHEMA_SCRIPT_PATH);
    executeSqlScript(CREATE_CONFIGURATION_ENTRIES_SCRIPT_PATH);
    executeSqlScript(CIRCULATION_SETTINGS_MIGRATION_SCRIPT_PATH);
    assertThat(getCirculationSettings(), hasSize(5));
    verifySettingsMigratedFromModConfiguration();
  }

  @Test
  public void settingsFromModSettingsAreMigrated() {
    executeSqlScript(CREATE_MOD_SETTINGS_SCHEMA_SCRIPT_PATH);
    executeSqlScript(CREATE_SETTINGS_ENTRIES_SCRIPT_PATH);
    executeSqlScript(CIRCULATION_SETTINGS_MIGRATION_SCRIPT_PATH);
    assertThat(getCirculationSettings(), hasSize(2));
    verifySettingsMigratedFromModSettings();
  }

  @Test
  public void settingsFromBothModulesAreMigrated() {
    executeSqlScript(CREATE_MOD_CONFIGURATION_SCHEMA_SCRIPT_PATH);
    executeSqlScript(CREATE_CONFIGURATION_ENTRIES_SCRIPT_PATH);
    executeSqlScript(CREATE_MOD_SETTINGS_SCHEMA_SCRIPT_PATH);
    executeSqlScript(CREATE_SETTINGS_ENTRIES_SCRIPT_PATH);
    executeSqlScript(CIRCULATION_SETTINGS_MIGRATION_SCRIPT_PATH);

    assertThat(getCirculationSettings(), hasSize(7));
    verifySettingsMigratedFromModConfiguration();
    verifySettingsMigratedFromModSettings();

    // run the script again and verify that no duplicates are created
    executeSqlScript(CIRCULATION_SETTINGS_MIGRATION_SCRIPT_PATH);
    assertThat(getCirculationSettings(), hasSize(7));
    verifySettingsMigratedFromModConfiguration();
    verifySettingsMigratedFromModSettings();
  }

  private void verifySettingsMigratedFromModConfiguration() {
    verifyThatCirculationSettingExists("TLR", new JsonObject()
      .put("titleLevelRequestsFeatureEnabled", true)
      .put("createTitleLevelRequestsByDefault", true)
      .put("tlrHoldShouldFollowCirculationRules", false)
      .put("confirmationPatronNoticeTemplateId", "60c7b7f8-b801-4dc5-a145-f89ec01c19cc")
      .put("cancellationPatronNoticeTemplateId", null)
      .put("expirationPatronNoticeTemplateId", "60c7b7f8-b801-4dc5-a145-f89ec01c19cc"));

    verifyThatCirculationSettingExists("PRINT_HOLD_REQUESTS", new JsonObject()
      .put("printHoldRequestsEnabled", false));

    verifyThatCirculationSettingExists("noticesLimit", new JsonObject()
      .put("value", "99"));

    verifyThatCirculationSettingExists("other_settings", new JsonObject()
      .put("audioAlertsEnabled", false)
      .put("audioTheme", "classic")
      .put("checkoutTimeout", true)
      .put("checkoutTimeoutDuration", 4)
      .put("prefPatronIdentifier", "barcode")
      .put("useCustomFieldsAsIdentifiers", false)
      .put("wildcardLookupEnabled", false));

    verifyThatCirculationSettingExists("loan_history", new JsonObject()
      .put("closingType", new JsonObject()
        .put("loan", "immediately")
        .put("feeFine", null)
        .put("loanExceptions", new JsonArray()))
      .put("loan", new JsonObject())
      .put("feeFine", new JsonObject())
      .put("loanExceptions", new JsonArray())
      .put("treatEnabled", false)
    );
  }

  private void verifySettingsMigratedFromModSettings() {
    verifyThatCirculationSettingExists("regularTlr", new JsonObject()
      .put("expirationPatronNoticeTemplateId", null)
      .put("cancellationPatronNoticeTemplateId", "dceb5fbf-cb10-4d03-8691-6df46c67dad9")
      .put("confirmationPatronNoticeTemplateId", "dceb5fbf-cb10-4d03-8691-6df46c67dad9"));

    verifyThatCirculationSettingExists("generalTlr", new JsonObject()
      .put("titleLevelRequestsFeatureEnabled", true)
      .put("createTitleLevelRequestsByDefault", false)
      .put("tlrHoldShouldFollowCirculationRules", false));
  }

  @SneakyThrows
  private static Collection<CirculationSetting> getCirculationSettings() {
    return pgClient.get(CIRCULATION_SETTINGS_TABLE, CirculationSetting.class, new Criterion())
      .toCompletionStage()
      .toCompletableFuture()
      .get(5, SECONDS)
      .getResults();
  }

  private void verifyThatCirculationSettingExists(String name, JsonObject expectedValue) {
    Map<String, CirculationSetting> settingsByName = getCirculationSettings()
      .stream()
      .collect(toMap(CirculationSetting::getName, identity()));

    CirculationSetting setting = settingsByName.get(name);
    assertThat(setting, notNullValue());
    assertThat(setting.getId(), notNullValue());
    JsonObject actualValue = new JsonObject(setting.getValue().getAdditionalProperties());
    assertThat(actualValue, equalTo(expectedValue));
  }

}
