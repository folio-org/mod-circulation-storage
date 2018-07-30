CREATE UNIQUE INDEX request_itemid_position_idx_unique
  ON ${myuniversity}_${mymodule}.request((jsonb->'itemId'), (jsonb->'position'));
