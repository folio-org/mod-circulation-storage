CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.${table.tableName}_update() RETURNS TRIGGER AS $$

BEGIN
  IF OLD.jsonb->>'status' = 'Open - Awaiting pickup' AND
    NEW.jsonb->>'status' IN ('Closed - Pickup expired', 'Closed - Cancelled') THEN
    NEW.jsonb = jsonb_set(NEW.jsonb, '{awaitingPickupRequestClosedDate}', to_jsonb(current_timestamp), true);
  	END IF;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';


DROP TRIGGER IF EXISTS ${table.tableName}_update_trigger ON ${myuniversity}_${mymodule}.${table.tableName};


CREATE TRIGGER ${table.tableName}_update_trigger
BEFORE
UPDATE ON ${myuniversity}_${mymodule}.${table.tableName}
FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.${table.tableName}_update();

