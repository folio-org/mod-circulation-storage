CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set the new schema first so that we dont have to namespace when creating tables
-- add the postgres to the search path so that we can use the pgcrypto extension
SET search_path TO myuniversity_mymodule, public;

-- audit table to keep a history of the changes
-- made to a record.
CREATE TABLE IF NOT EXISTS loan_history_table (
   _id UUID PRIMARY KEY,
   orig_id UUID NOT NULL,
   operation char(1) NOT NULL,
   jsonb jsonb,
   created_date timestamp not null
   );

CREATE OR REPLACE FUNCTION loan_history_func() RETURNS TRIGGER AS $loan_history$
		DECLARE
		  injectedAction text;
    BEGIN
        IF (TG_OP = 'DELETE') THEN
            injectedAction = '"deleted"';
            OLD.jsonb = jsonb_set(OLD.jsonb, '{action}' , injectedAction::jsonb , false);
            INSERT INTO myuniversity_mymodule.loan_history_table SELECT gen_random_uuid(), OLD._id, 'D', OLD.jsonb, current_timestamp;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            INSERT INTO myuniversity_mymodule.loan_history_table SELECT gen_random_uuid(), NEW._id, 'U', NEW.jsonb, current_timestamp;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            INSERT INTO myuniversity_mymodule.loan_history_table SELECT gen_random_uuid(), NEW._id, 'I', NEW.jsonb, current_timestamp;
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$loan_history$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS loan_history_trigger ON loan CASCADE;

CREATE TRIGGER loan_history_trigger AFTER INSERT OR UPDATE OR DELETE ON loan FOR EACH ROW EXECUTE PROCEDURE loan_history_func();

GRANT ALL PRIVILEGES ON loan_history_table TO myuniversity_mymodule;
