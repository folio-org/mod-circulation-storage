CREATE INDEX IF NOT EXISTS request_itemid_eq_idx
  ON ${myuniversity}_${mymodule}.request ((jsonb->>'itemId'));

