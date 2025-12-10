-- copy circulation configuration entries from mod-configuration
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = '${myuniversity}_mod_configuration'
    AND table_name = 'config_data'
  ) THEN
    EXECUTE '
      INSERT INTO ${myuniversity}_${mymodule}.circulation_settings (id, jsonb)
      SELECT
        (new_jsonb->>''id'')::uuid AS id,
        new_jsonb AS jsonb
      FROM (
        SELECT
          jsonb_build_object(
            ''id'', gen_random_uuid(),
            ''name'', jsonb->>''configName'',
            ''value'', CASE
              WHEN jsonb->>''value'' IS JSON OBJECT
              THEN (jsonb->>''value'')::jsonb
              ELSE jsonb_build_object(''value'', jsonb->''value'')
            END
          ) AS new_jsonb
        FROM ${myuniversity}_mod_configuration.config_data
        WHERE
          (
            (jsonb->>''module'' = ''SETTINGS'' AND jsonb->>''configName'' IN (''TLR'', ''PRINT_HOLD_REQUESTS''))
            OR (jsonb->>''module'' = ''NOTIFICATION_SCHEDULER'' AND jsonb->>''configName'' = ''noticesLimit'')
            OR (jsonb->>''module'' = ''CHECKOUT'' AND jsonb->>''configName'' = ''other_settings'')
            OR (jsonb->>''module'' = ''LOAN_HISTORY'' AND jsonb->>''configName'' = ''loan_history'')
          )
          AND NOT EXISTS (
            SELECT 1
            FROM ${myuniversity}_${mymodule}.circulation_settings
            WHERE jsonb->>''name'' = config_data.jsonb->>''configName''
          )
      )';
  END IF;
END $$;

-- copy circulation settings from mod-settings
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = '${myuniversity}_mod_settings'
    AND table_name = 'settings'
  ) THEN
    EXECUTE '
      INSERT INTO ${myuniversity}_${mymodule}.circulation_settings (id, jsonb)
      SELECT
        (new_jsonb->>''id'')::uuid AS id,
        new_jsonb AS jsonb
      FROM (
        SELECT
          jsonb_build_object(
            ''id'', gen_random_uuid(),
            ''name'', key,
            ''value'', value->''value''
          ) AS new_jsonb
        FROM ${myuniversity}_mod_settings.settings
        WHERE
          scope = ''circulation''
          AND key IN (''regularTlr'', ''generalTlr'')
          AND NOT EXISTS (
            SELECT 1
            FROM ${myuniversity}_${mymodule}.circulation_settings
            WHERE jsonb->>''name'' = settings.key
          )
      )';
  END IF;
END $$;
