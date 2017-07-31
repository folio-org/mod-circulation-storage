CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

CREATE ROLE myuniversity_mymodule PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

GRANT myuniversity_mymodule TO CURRENT_USER;

CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.loan (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

CREATE TABLE myuniversity_mymodule.loan_policy (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

CREATE TABLE myuniversity_mymodule.loan_rules (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

CREATE TABLE myuniversity_mymodule.request (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

INSERT INTO myuniversity_mymodule.loan_rules
  SELECT id, jsonb_build_object('id', id, 'loanRulesAsTextFile', '')
  FROM (SELECT gen_random_uuid() AS id) AS alias;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA myuniversity_mymodule TO myuniversity_mymodule;
