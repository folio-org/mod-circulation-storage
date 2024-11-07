INSERT INTO ${myuniversity}_${mymodule}.staff_slips(id, jsonb)
VALUES ('e6e29ec1-1a76-4913-bbd3-65f4ffd94e04',
        jsonb_build_object(
          'id', 'e6e29ec1-1a76-4913-bbd3-65f4ffd94e04',
          'name', 'Transit (mediated requests)',
          'active', true,
          'template', '<p></p>'))
ON CONFLICT DO NOTHING;