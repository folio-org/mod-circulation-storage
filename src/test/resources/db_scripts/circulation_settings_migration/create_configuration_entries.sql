INSERT INTO ${myuniversity}_mod_configuration.config_data(id, jsonb) VALUES

('6a2e8459-8028-4567-a788-6a6ebc8eb041'::uuid,
  CAST('
    {
      "id": "6a2e8459-8028-4567-a788-6a6ebc8eb041",
      "value": "{\"titleLevelRequestsFeatureEnabled\":true,\"createTitleLevelRequestsByDefault\":true,\"tlrHoldShouldFollowCirculationRules\":false,\"confirmationPatronNoticeTemplateId\":\"60c7b7f8-b801-4dc5-a145-f89ec01c19cc\",\"cancellationPatronNoticeTemplateId\":null,\"expirationPatronNoticeTemplateId\":\"60c7b7f8-b801-4dc5-a145-f89ec01c19cc\"}",
      "module": "SETTINGS",
      "configName": "TLR",
      "enabled": true,
      "metadata": {
        "createdDate": "2025-11-11T15:55:49.076Z",
        "updatedDate": "2025-11-11T15:55:49.076Z",
        "createdByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e",
        "updatedByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e"
      }
    }'
  AS jsonb)),

('c4938d4d-df56-48e5-999a-efa33e00a02b'::uuid,
  CAST('
    {
      "id": "c4938d4d-df56-48e5-999a-efa33e00a02b",
      "value": "{\"printHoldRequestsEnabled\":false}",
      "module": "SETTINGS",
      "configName": "PRINT_HOLD_REQUESTS",
      "enabled": true,
      "metadata": {
        "createdDate": "2025-11-11T15:00:08.444Z",
        "updatedDate": "2025-11-11T15:46:11.894Z",
        "createdByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e",
        "updatedByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e"
      }
    }'
  AS jsonb)),

('8e238098-8934-4ba6-942b-76a6ff11ae65'::uuid,
  CAST('
    {
      "id": "8e238098-8934-4ba6-942b-76a6ff11ae65",
      "value": "99",
      "module": "NOTIFICATION_SCHEDULER",
      "configName": "noticesLimit",
      "enabled": true,
      "metadata": {
        "createdDate": "2025-11-11T15:00:08.444Z",
        "updatedDate": "2025-11-11T15:46:11.894Z",
        "createdByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e",
        "updatedByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e"
      }
    }'
  AS jsonb)),

('719f470c-ed00-426d-aab3-7e32a21d4beb'::uuid,
  CAST('
    {
      "id": "719f470c-ed00-426d-aab3-7e32a21d4beb",
      "value": "{\"audioAlertsEnabled\":false,\"audioTheme\":\"classic\",\"checkoutTimeout\":true,\"checkoutTimeoutDuration\":4,\"prefPatronIdentifier\":\"barcode\",\"useCustomFieldsAsIdentifiers\":false,\"wildcardLookupEnabled\":false}",
      "module": "CHECKOUT",
      "configName": "other_settings",
      "enabled": true,
      "metadata": {
        "createdDate": "2025-11-11T15:00:08.444Z",
        "updatedDate": "2025-11-11T15:46:11.894Z",
        "createdByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e",
        "updatedByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e"
      }
    }'
  AS jsonb)),

('e1d31af5-0c0c-4437-ad52-1588306bbca7'::uuid,
  CAST('
    {
      "id": "e1d31af5-0c0c-4437-ad52-1588306bbca7",
      "module": "LOAN_HISTORY",
      "configName": "loan_history",
      "enabled": true,
      "value": "{\"closingType\":{\"loan\":\"immediately\",\"feeFine\":null,\"loanExceptions\":[]},\"loan\":{},\"feeFine\":{},\"loanExceptions\":[],\"treatEnabled\":false}",
      "metadata": {
        "createdDate": "2025-11-12T13:23:31.077+00:00",
        "createdByUserId": "e1d82db2-74d8-4200-82fd-92aced5484b1",
        "updatedDate": "2025-11-12T13:23:31.077+00:00",
        "updatedByUserId": "e1d82db2-74d8-4200-82fd-92aced5484b1"
      }
    }'
  AS jsonb)),


-- this record should not be migrated
('a475f75b-78eb-4c8e-9579-442528ccd80b'::uuid,
  CAST('
    {
      "id": "a475f75b-78eb-4c8e-9579-442528ccd80b",
      "value": "{\"randomProperty\":\"randomValue\"}",
      "module": "TEST",
      "configName": "RANDOM_CONFIG",
      "enabled": true,
      "metadata": {
        "createdDate": "2025-11-11T15:00:08.444Z",
        "updatedDate": "2025-11-11T15:46:11.894Z",
        "createdByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e",
        "updatedByUserId": "f6d1fc5e-63cd-4f84-b76a-7d7080b2fb1e"
      }
    }'
  AS jsonb));
