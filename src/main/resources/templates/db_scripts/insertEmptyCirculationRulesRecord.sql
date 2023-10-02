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
  SELECT id, jsonb_build_object('id', id, 'rulesAsText', '')
  FROM (SELECT md5('${myuniversity}_${mymodule}.${table.tableName}.rulesAsText')::uuid AS id) AS alias
  ON CONFLICT DO NOTHING;
