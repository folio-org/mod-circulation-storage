INSERT INTO ${myuniversity}_${mymodule}.staff_slips(id, jsonb) VALUES (
    '8812bae1-2738-442c-bc20-fe4bb38a11f8',
    jsonb_build_object(
        'id', '8812bae1-2738-442c-bc20-fe4bb38a11f8',
        'name', 'Pick slip',
        'active', true,
        'template', '<p></p>')) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.staff_slips(id, jsonb) VALUES (
    '1ed55c5c-64d9-40eb-8b80-7438a262288b',
	jsonb_build_object(
        'id', '1ed55c5c-64d9-40eb-8b80-7438a262288b',
        'name', 'Request delivery',
        'active', true,
        'template', '<p></p>')) ON CONFLICT DO NOTHING;
