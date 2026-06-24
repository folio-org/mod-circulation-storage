-- ensure that there will be one row only
DO $$
BEGIN
  -- create constraints only if they do not exist, otherwise they are piling up
  IF NOT EXISTS (
    SELECT constraint_name
    FROM information_schema.constraint_column_usage
    WHERE table_schema = '${myuniversity}_${mymodule}'
    AND table_name = '${table.tableName}'
    AND column_name = 'lock'
    AND constraint_name LIKE '${table.tableName}_lock_%'
  ) THEN
    ALTER TABLE ${myuniversity}_${mymodule}.${table.tableName}
    -- this will create constraints even when column already exists
    ADD COLUMN IF NOT EXISTS lock boolean DEFAULT true UNIQUE CHECK(lock=true);
  END IF;
END $$;
INSERT INTO ${myuniversity}_${mymodule}.${table.tableName}
  SELECT id, jsonb_build_object(
    'id', id,
    'rulesAsText', E'priority: number-of-criteria, criterium (t, s, c, b, a, m, g), last-line\nfallback-policy: l d9cd0bed-1b49-4b5e-a7bd-064b8d177231 r 334e5a9e-94f9-4673-8d1d-ab552863886b n 122b3d2b-4788-4f1e-9117-56daa91cb75c o cd3f6cac-fa17-4079-9fae-2fb28e521412 i ed892c0e-52e0-4cd9-8133-c0ef07b4a709\nm 1a54b431-2e4f-452d-9cae-9cee66c9a892: l 43198de5-f56a-4a53-a0bd-5a324418967a r 334e5a9e-94f9-4673-8d1d-ab552863886b n 122b3d2b-4788-4f1e-9117-56daa91cb75c o cd3f6cac-fa17-4079-9fae-2fb28e521412 i ed892c0e-52e0-4cd9-8133-c0ef07b4a709')
  FROM (SELECT md5('${myuniversity}_${mymodule}.${table.tableName}.rulesAsText')::uuid AS id) AS alias
  ON CONFLICT DO NOTHING;
