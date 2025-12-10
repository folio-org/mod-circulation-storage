CREATE SCHEMA IF NOT EXISTS ${myuniversity}_mod_configuration;

CREATE TABLE IF NOT EXISTS ${myuniversity}_mod_configuration.config_data
(
    id uuid NOT NULL,
    jsonb jsonb NOT NULL,
    creation_date timestamp without time zone,
    created_by text COLLATE pg_catalog."default",
    CONSTRAINT config_data_pkey PRIMARY KEY (id)
)
