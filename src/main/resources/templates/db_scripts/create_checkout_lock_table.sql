CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.check_out_lock (
  id UUID PRIMARY KEY,
  user_id UUID UNIQUE NOT NULL,
  creation_date TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
