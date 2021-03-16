INSERT INTO ${myuniversity}_${mymodule}.staff_slips(id, jsonb) VALUES (
    '6a6e72f0-69da-4b4c-8254-7154679e9d88',
    jsonb_build_object(
        'id', '6a6e72f0-69da-4b4c-8254-7154679e9d88',
        'name', 'Hold',
        'active', true,
        'template', '<p></p>')) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.staff_slips(id, jsonb) VALUES (
    'f838cdaf-555a-473f-abf1-f35ef6ab8ae1',
    jsonb_build_object(
        'id', 'f838cdaf-555a-473f-abf1-f35ef6ab8ae1',
        'name', 'Transit',
        'active', true,
        'template', '<p></p>')) ON CONFLICT DO NOTHING;
