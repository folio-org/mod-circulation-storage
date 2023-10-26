UPDATE request
SET jsonb = jsonb - 'fulfilmentPreference' || jsonb_build_object('fulfillmentPreference', jsonb->'fulfilmentPreference')
WHERE jsonb->'fulfilmentPreference' IS NOT NULL;
