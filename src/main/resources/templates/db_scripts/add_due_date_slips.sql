INSERT INTO ${myuniversity}_${mymodule}.staff_slips(id, jsonb)
VALUES ('0b52bca7-db17-4e91-a740-7872ed6d7323',
        jsonb_build_object(
          'id', '0b52bca7-db17-4e91-a740-7872ed6d7323',
          'name', 'Due date receipt',
          'active', true,
          'template', '<p></p>'))
ON CONFLICT DO NOTHING;
