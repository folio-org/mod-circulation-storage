CREATE ROLE myuniversity_mymodule PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.loan (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

GRANT ALL ON myuniversity_mymodule.loan TO myuniversity_mymodule;
