    -- Create an equality index on request.itemId to support fast lookups by itemId.
    -- The existing request_itemid_idx uses lower(f_unaccent(...)) which does not match
    -- the plain equality query (jsonb->>'itemId') = ? generated during item update events
    -- (e.g. from mod-bulk-operations), resulting in sequential scans and high DB load.
    -- See: https://folio-org.atlassian.net/browse/CIRC-XXXX
    DO $do$
    BEGIN
      PERFORM rmb_internal_index(
      'request',                    'request_itemid_eq_idx', 'ADD',
      'CREATE INDEX IF NOT EXISTS request_itemid_eq_idx ON ${myuniversity}_${mymodule}.request '
      || $rmb$((jsonb->>'itemId'))$rmb$);
    END $do$;

