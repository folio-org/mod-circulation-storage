INSERT INTO ${myuniversity}_mod_settings.settings(id, scope, key, value) VALUES

('2dc1f861-07ef-4028-9fd1-a8a74a27d729'::uuid, 'circulation', 'regularTlr',
  CAST('
    {
      "value": {
        "expirationPatronNoticeTemplateId": null,
        "cancellationPatronNoticeTemplateId": "dceb5fbf-cb10-4d03-8691-6df46c67dad9",
        "confirmationPatronNoticeTemplateId": "dceb5fbf-cb10-4d03-8691-6df46c67dad9"
      }
    }'
  AS jsonb)),

('fd2a7052-aae3-4ea6-9dd7-d354813573d4'::uuid, 'circulation', 'generalTlr',
  CAST('
	  {
      "value": {
        "titleLevelRequestsFeatureEnabled": true,
        "createTitleLevelRequestsByDefault": false,
        "tlrHoldShouldFollowCirculationRules": false
      }
   }'
   AS jsonb)),

-- this record should not be migrated
('c6b4ecc3-5990-461c-83ec-8ea639086165'::uuid, 'random', 'test',
  CAST('
	  {
	    "value": {
        "randomProperty": "randomValue"
      }
    }'
  AS jsonb));
