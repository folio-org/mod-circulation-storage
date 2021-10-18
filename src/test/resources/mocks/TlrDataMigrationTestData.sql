DO
$do$
    DECLARE u uuid;
BEGIN
FOR i IN 1..55 LOOP
    u := uuid_generate_v4();
    insert into test_tenant_mod_circulation_storage.request (id, jsonb) values
    (
      uuid_generate_v4(),
      '{
        "item": {
            "title": "Test title",
            "barcode": "987654",
            "identifiers": [
                {
                    "value": "0262012103",
                    "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"
                },
                {
                    "value": "9780262012102",
                    "identifierTypeId": "8261054f-be78-422d-bd51-4ed9f33c3422"
                },
                {
                    "value": "2003065165",
                    "identifierTypeId": "c858e4f2-2b6b-4385-842b-60732ee14abb"
                }
            ]
        },
        "itemId": "100d10bf-2f06-4aa0-be15-0b95b2d9f9e3",
        "status": "Closed - Filled",
        "metadata": {
            "createdDate": "2021-03-18T09:55:56.152",
            "updatedDate": "2021-03-18T09:58:01.137+00:00",
            "createdByUserId": "c2d3902a-3454-50ea-998f-42897e32ac5f",
            "updatedByUserId": "c2d3902a-3454-50ea-998f-42897e32ac5f"
        },
        "requester": {
            "barcode": "123456",
            "lastName": "Lastname",
            "firstName": "Firstname"
        },
        "requestDate": "2021-03-18T09:55:53.000+00:00",
        "requestType": "Hold",
        "requesterId": "bec20636-fb68-41fd-84ea-2cf910673599",
        "fulfilmentPreference": "Hold Shelf",
        "pickupServicePointId": "3a40852d-49fd-4df2-a1f9-6e2641a6e91f",
        "requestExpirationDate": "2021-03-19T00:00:00.000+00:00",
        "holdShelfExpirationDate": "2021-04-08T23:59:59.551+00:00"
      }'
    );
END LOOP;
END
$do$;
