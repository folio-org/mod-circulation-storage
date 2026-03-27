    DO $do$
    BEGIN
      PERFORM rmb_internal_index(
      'request',                    'request_itemid_eq_idx', 'ADD',
      'CREATE INDEX IF NOT EXISTS request_itemid_eq_idx ON ${myuniversity}_${mymodule}.request '
      || $rmb$((jsonb->>'itemId'))$rmb$);
    END $do$;

