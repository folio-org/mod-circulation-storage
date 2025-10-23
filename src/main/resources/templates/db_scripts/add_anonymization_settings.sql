INSERT INTO ${myuniversity}_${mymodule}.anonymization_settings (id, jsonb)
VALUES (
         '0b52bca7-db17-4e91-a740-7872ed6d7323',
         jsonb_build_object(
           'id', '0b52bca7-db17-4e91-a740-7872ed6d7323',
           'anonymizeClosedRequests', false,
           'anonymizeCancelledRequests', false,
           'retentionPeriodDays', 0
         )
       )
  ON CONFLICT (id) DO NOTHING;
