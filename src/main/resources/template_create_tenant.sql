CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

CREATE ROLE myuniversity_mymodule PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

GRANT myuniversity_mymodule TO CURRENT_USER;

CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

CREATE TABLE myuniversity_mymodule.loan (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL,
  creation_date timestamp WITH TIME ZONE,
  created_by text
);

-- Only allow one open loan per item
CREATE UNIQUE INDEX only_one_open_loan_per_item
  ON myuniversity_mymodule.loan( (jsonb->>'itemId') )
  WHERE (jsonb->'status'->>'name') = 'Open';

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
  jsonb JSONB NOT NULL,
  creation_date timestamp WITH TIME ZONE,
  created_by text
);

CREATE TABLE myuniversity_mymodule.fixed_due_date_schedule (
  _id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

CREATE UNIQUE INDEX myuniversity_mymodule_fixed_due_date_schedule_unique_name
  ON myuniversity_mymodule.fixed_due_date_schedule( lower((jsonb->>'name')) );


INSERT INTO myuniversity_mymodule.loan_rules
  SELECT id, jsonb_build_object('id', id, 'loanRulesAsTextFile', '')
  FROM (SELECT gen_random_uuid() AS id) AS alias;

-- auto populate the meta data schema

-- on create of user record - pull creation date and creator into dedicated column - rmb makes auto-populates these fields in the md fields
CREATE OR REPLACE FUNCTION update_metadata_columns_trigger()
RETURNS TRIGGER AS $$
BEGIN
  NEW.creation_date = to_timestamp(NEW.jsonb->'metaData'->>'createdDate', 'YYYY-MM-DD"T"HH24:MI:SS.MS');
  NEW.created_by = NEW.jsonb->'metaData'->>'createdByUserId';
  RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER set_loans_metadata_columns_trigger BEFORE INSERT ON myuniversity_mymodule.loan FOR EACH ROW EXECUTE PROCEDURE update_metadata_columns_trigger();
CREATE TRIGGER set_requests_metadata_columns_trigger BEFORE INSERT ON myuniversity_mymodule.request FOR EACH ROW EXECUTE PROCEDURE update_metadata_columns_trigger();

-- on update populate md fields from the creation date and creator fields
CREATE OR REPLACE FUNCTION update_metadata_properties_in_json_trigger()
RETURNS TRIGGER AS $$
DECLARE
  createdDate timestamp WITH TIME ZONE;
  createdBy text ;
  updatedDate timestamp WITH TIME ZONE;
  updatedBy text ;
  injectedId text;
BEGIN
  createdBy = NEW.created_by;
  createdDate = NEW.creation_date;
  updatedDate = NEW.jsonb->'metaData'->>'updatedDate';
  updatedBy = NEW.jsonb->'metaData'->>'updatedByUserId';

  if createdBy ISNULL then
    createdBy = 'undefined';
  end if;
  if updatedBy ISNULL then
    updatedBy = 'undefined';
  end if;
  if createdDate IS NOT NULL then
-- creation date and update date will always be injected by rmb - if created date is null it means that there is no meta data object
-- associated with this object - so only add the meta data if created date is not null -- created date being null may be a problem
-- and should be handled at the app layer for now -- currently this protects against an exception in the db if no md is present in the json
    injectedId = '{"createdDate":"'||to_char(createdDate,'YYYY-MM-DD"T"HH24:MI:SS.MS')||'" , "createdByUserId":"'||createdBy||'", "updatedDate":"'||to_char(updatedDate,'YYYY-MM-DD"T"HH24:MI:SS.MSOF')||'" , "updatedByUserId":"'||updatedBy||'"}';
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metaData}' ,  injectedId::jsonb , false);
  end if;
RETURN NEW;

END;
$$ language 'plpgsql';

CREATE TRIGGER set_loans_metadata_properties_in_json_trigger BEFORE UPDATE ON myuniversity_mymodule.loan FOR EACH ROW EXECUTE PROCEDURE update_metadata_properties_in_json_trigger();
CREATE TRIGGER set_requests_metadata_properties_in_json_trigger BEFORE UPDATE ON myuniversity_mymodule.request FOR EACH ROW EXECUTE PROCEDURE update_metadata_properties_in_json_trigger();

-- --- end auto populate meta data schema ------------
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA myuniversity_mymodule TO myuniversity_mymodule;
