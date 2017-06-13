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

GRANT ALL ON myuniversity_mymodule.loan TO myuniversity_mymodule;
GRANT ALL ON myuniversity_mymodule.loan_policy TO myuniversity_mymodule;
