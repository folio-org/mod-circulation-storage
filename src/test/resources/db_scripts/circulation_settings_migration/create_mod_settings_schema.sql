CREATE SCHEMA IF NOT EXISTS ${myuniversity}_mod_settings;

CREATE TABLE IF NOT EXISTS ${myuniversity}_mod_settings.settings
(
    id uuid NOT NULL,
    scope character varying COLLATE pg_catalog."default" NOT NULL,
    key character varying COLLATE pg_catalog."default" NOT NULL,
    value jsonb NOT NULL,
    userid uuid,
    CONSTRAINT settings_pkey PRIMARY KEY (id)
)
