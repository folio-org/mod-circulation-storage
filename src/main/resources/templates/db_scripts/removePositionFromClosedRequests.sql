UPDATE ${myuniversity}_${mymodule}.request
SET jsonb = jsonb - 'position'
WHERE jsonb->>'status' IN ('Closed - Unfilled', 'Closed - Pickup expired');