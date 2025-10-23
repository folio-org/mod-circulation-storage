INSERT INTO ${myuniversity}_${mymodule}.request_anonymization_settings (id, jsonb)
SELECT id,
       jsonb_build_object(
         'enabled', COALESCE( (jsonb->>'enabled')::boolean, true),
         'retentionDays', COALESCE( (jsonb->>'retentionDays')::int,
                                    (jsonb->>'retentionPeriodDays')::int, 0)
       )
FROM ${myuniversity}_${mymodule}.anonymization_settings
  ON CONFLICT (id) DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.request_anonymization_settings (id, jsonb)
VALUES ('0b52bca7-db17-4e91-a740-7872ed6d7323',
        jsonb_build_object('enabled', false, 'retentionDays', 0))
  ON CONFLICT (id) DO NOTHING;

SELECT id, jsonb
FROM ${myuniversity}_${mymodule}.request_anonymization_settings
WHERE id = '0b52bca7-db17-4e91-a740-7872ed6d7323'
