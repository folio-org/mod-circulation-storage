    -- Create index for agedToLostDelayedBilling.dateLostItemShouldBeBilled on loan table.
    -- Needed to set a shorter custom index name, otherwise PostgreSQL may truncate it:
    -- https://issues.folio.org/browse/RMB-689
    -- This SQL must always run (no "fromModuleVersion") to signal RMB to create the index,
    -- to signal RMB to not delete the index, and the SQL must run after table creation.
    DO $do$
    BEGIN
      PERFORM rmb_internal_index(
      'loan',                    'loan_dateLostItemShouldBeBilled_idx', 'ADD',
      'CREATE INDEX IF NOT EXISTS loan_dateLostItemShouldBeBilled_idx ON ${myuniversity}_${mymodule}.loan '
      || $rmb$(lower(${myuniversity}_${mymodule}.f_unaccent(jsonb->'agedToLostDelayedBilling'->>'dateLostItemShouldBeBilled')))$rmb$);
    END $do$;
