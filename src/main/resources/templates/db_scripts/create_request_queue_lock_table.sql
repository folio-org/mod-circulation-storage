CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.request_queue_lock (
  id UUID PRIMARY KEY,
  queue_type TEXT NOT NULL,
  queue_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT request_queue_lock_queue_type_check
    CHECK (queue_type IN ('INSTANCE', 'ITEM')),
  CONSTRAINT request_queue_lock_queue_key_unique
    UNIQUE (queue_type, queue_id)
);
