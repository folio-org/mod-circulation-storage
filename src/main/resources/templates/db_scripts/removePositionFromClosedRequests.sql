UPDATE ${myuniversity}_${mymodule}.request
SET jsonb = jsonb - 'position'
WHERE jsonb->>'position' IS NOT NULL
AND jsonb->>'status' IN ('Closed - Unfilled', 'Closed - Pickup expired', 'Closed - Cancelled', 'Closed - Filled');