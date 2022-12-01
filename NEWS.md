## 15.0.2 2022-12-01

* Nolana bugfest missing index that is present in clean Nolana deployment (CIRCSTORE-375)

## 15.0.1 2022-11-07

* mod-pubsub-client 2.7.0, Spring 5.3.23 fixing vulnerabilities (CIRCSTORE-371)

## 15.0.0 2022-10-18

* Extend actual cost record schema (CIRCSTORE-349, CIRCSTORE-358, CIRCSTORE-362)
* Allow compilation on ARM64 (CIRCSTORE-355)
* Support holdings-storage 6.0, item-storage 10.0 (CIRCSTORE-357)
* Update metadata.updatedDate upon request expiration (CIRCSTORE-350)
* Make actual cost record ID optional (CIRCSTORE-361)
* Update actual-cost-record-storage interface version (CIRCSTORE-364)
* Upgrade to RMB 35.0.0 and Vertx 4.3.3 (CIRCSTORE-363)

## 14.1.0 2022-06-27

* Replace slf4j-log4j12/slf4j-api by log4j-slf4j-impl (CVE-2019-17571) (CIRCSTORE-325)
* Add patron-action-session-storage.expired-session-patron-ids.collection.get to perm set (CIRCSTORE-326)
* Create TLR feature toggle job POST, GET endpoints (CIRCSTORE-327)
* Create a timer for TLR feature toggle jobs (CIRCSTORE-328)
* Implement TLR feature toggle job service (CIRCSTORE-329)
* Create actual-cost-record entity CRUD (CIRCSTORE-331)
* Add `requestLevel` field of `request` object to database schema (CIRCSTORE-333)
* Add index for `loan.agedToLostDelayedBilling.dateLostItemShouldBeBilled` field (CIRCSTORE-335)
* Add `Title level request expiration` scheduled notice triggering event (CIRCSTORE-339)
* Use default UUID for missing `instanceId` and `holdingsRecordId` during TLR data migration (CIRCSTORE-340)
* Add TLR feature toggle job error handling and tests (CIRCSTORE-341)
* Expire requests for only one tenant in the expiration job (CIRCSTORE-342)
* Handle invalid requests properly during TLR data migration (CIRCSTORE-344)
* Update to RMB 34.0.0 (CIRCSTORE-345)

## 14.0.0 2022-02-22

* Add Kafka message queues to select circulation storage record types (UXPROD-3380)
* Add a dependency on mod-inventory-storage interfaces (CIRCSTORE-320)
* Create likeIndex for itemId to improve performance of Items In Transit Report (CIRCSTORE-321)
* Upgrade to RMB 33.2.4 (CIRCSTORE-318)
* TLR feature (UXPROD-1796)
* Use new api-lint and api-doc CI facilities (FOLIO-3231)
* Verbose priority line in default circulation rules (CIRCSTORE-317)
* TLR data migration (UXPROD-3330)
* Upgrade to RMB 33.1.3 (CIRCSTORE-312)
* Upgrade to RMB 33.1.1, Vert.x 4.2.1, Spring 5.2.18, PubSub 2.4.0 (CIRCSTORE-304)
* Remove embed_postgres
* Set metadata in POST request storage batch endpoint (CIRCSTORE-288)

## 13.1.0 2021-09-30

* Fixes to code coverage, docker containers, and code owners (CIRCSTORE-289, CIRCSTORE-287, CIRCSTORE-259, CIRCSTORE-281)

## 13.0.0 2021-06-11

* Includes migration for missing staff slips (CIRCSTORE-273)
* Pickup expired not showing in Circulation log (CIRCSTORE-265)
* Use normalization for request.item.identifiers (for search requests by ISBN) (CIRCSTORE-186)
* `embed_postgres` command line option is no longer supported (CIRCSTORE-280)
* Upgrades to RAML Module Builder 33.0.0 (CIRCSTORE-280)
* Upgrades to 4.1.0.CR1 (CIRCSTORE-280)

## 12.2.0 2021-03-09

* Requests may include patron comments (CIRCSTORE-255)
* Loan policies can define whether requests can extend overdue loans (CIRCSTORE-257)
* Patron notice policies can define aged to lost related notices (CIRCSTORE-248, CIRCSTORE-264)
* Publishes circulation log messages to pub-sub (CIRCSTORE-247)
* Anonymizes loan history as well as loans (CIRCSTORE-260)
* Uses a `_timer` interface to periodically expire requests every two minutes (CIRCSTORE-247)
* Upgrades to RAML Module Builder 32.1.0 (CIRCSTORE-266)
* Upgrades to vert.x 4.0.0 (CIRCSTORE-266)
* Provides `loan-policy-storage 2.3`
* Provides `request-storage 3.4`
* Provides `patron-notice-policy-storage 0.13`
* Provides `_tenant 2.0`
* Requires `pubsub-event-types 0.1`
* Requires `pubsub-publishers 0.1`
* Requires `pubsub-subscribers 0.1`
* Requires `pubsub-publish 0.1`

## 12.1.1 2020-10-29

* Upgrade to RMB 31.1.4 and Vert.x 3.9.4 (CIRCSTORE-249), most notable fixes:
* Fix "tuple concurrently updated" when upgrading Q2 to Q3 with "REVOKE" (RMB-744)
* Use FOLIO fork of vertx-sql-client and vertx-pg-client (RMB-740) with the following two patches
* Make RMB's DB\_CONNECTIONRELEASEDELAY work again, defaults to 60 seconds (RMB-739)
* Fix duplicate names causing 'prepared statement "XYZ" already exists' (FOLIO-2840)
* Upgrade to Vert.x 3.9.4, fixes premature closing of RowStream https://github.com/eclipse-vertx/vertx-sql-client/issues/778 (RMB-738)

## 12.1.0 2020-10-06

* Introduces `aged to lost date` property on loans (CIRCSTORE-231)
* Added a variety of database indexes for loans and requests (CIRCSTORE-215, CIRCSTORE-219, CIRCSTORE-223, CIRCSTORE-228, CIRCSTORE-230)
* Requires JDK 11 (CIRCSTORE-235)
* Upgraded to RAML Module Builder 31.0.2 (CIRCSTORE-220, CIRCSTORE-232, CIRCSTORE-237)

## 12.0.0 2020-06-10

* Renames `itemEffectiveLocationAtCheckOut` to `itemEffectiveLocationIdAtCheckOut` in loan record (CIRCSTORE-208)
* Introduces delayed billing information for loan (CIRCSTORE-199)
* Introduces notices for overdue fees / fines (CIRCSTORE-207)
* Creates `request delivery` and `pick slip` staff slip templates during upgrade (CIRCSTORE-200)
* Provides `loan-storage 7.0`
* Provides `patron-notice-storage-policy 0.12`
* Provides `scheduled-notice-storage 0.4`
* Upgrades to RAML Module Builder 30.0.0 (CIRCSTORE-212)

## 11.0.0 2020-03-09

* Stores `overdue fine policy` and `lost item policy` for loan (CIRCSTORE-177)
* Stores `ISBN identifiers` for loaned item (CIRCSTORE-181)
* Stores `claimed returned date` for loan (CIRCSTORE-187)
* Stores a record of a check in having occurred (CIRCSTORE-193, CIRCSTORE-196)
* Make full text indexes use the simple dictionary (CIRCSTORE-178)
* Introduces indexes on various loan properties (CIRCSTORE-184)
* Includes `overdue fine policy` and `lost item policy` in default rules (CIRCSTORE-192)
* Upgrades to `RAML Module Builder 29.2.2` (CIRCSTORE-183)
* Upgrades to `Vert.x 3.8.4`
* Provides `loan-storage 6.6`
* Provides `request-storage 3.3`
* Provides `request-storage-batch 0.3`
* Provides `check-in-storage 0.2`

## 10.0.0 2019-11-28

* Introduces check in / check out patron sessions (CIRCSTORE-147, CIRCSTORE-148, CIRCSTORE-149)
* Introduces user request preferences (CIRCSTORE-154, CIRCSTORE-165)
* Introduces batch requests API (CIRCSTORE-164)
* Introduces `item effective location at check out` property on `loan` record (CIRCSTORE-157)
* Introduces `item declared lost date` property on `loan` record (CIRCSTORE-175)
* Introduces `Open - Awaiting delivery` request status (CIRCSTORE-169)
* Introduces `item limit` for loan policies (CIRCSTORE-170)
* Improves patron notice policy validation (CIRCSTORE-155, CIRCSTORE-158)
* Fixes hold shelf expiration bug which caused some requests to not expire (CIRCSTORE-163)
* Disallows alternate renewal period for fixed profile loan policies (CIRCSTORE-159)
* Includes default overdue fines and lost item fees policies in sample circulation rules (
CIRCSTORE-160, CIRCSTORE-171)
* Changes container memory management (CIRCSTORE-173, FOLIO-2358)
* Provides `loan-storage 6.4`
* Provides `request-storage 3.2`
* Provides `loan-policy-storage 2.1`
* Provides `patron-notice-policy-storage 0.11`
* Provides `scheduled-notice-storage 0.3`
* Provides `patron-action-session-storage 0.2`
* Provides `anonymize-storage-loans 0.1`
* Provides `request-preference-storage 2.0`
* Provides `request-storage-batch 0.2`

## 9.3.0 2019-09-18

* Stores whether a loan `due date has changed due to a recall` request (CIRCSTORE-156)
* Adds `Manual due date change` event to patron notice policies (CIRCSTORE-151)

## 9.2.0 2019-09-09

* Can anonymize a list of loans (CIRCSTORE-137)
* Adds `recipient user ID` to `scheduled notices` (CIRCSTORE-146)
* Adds `request expiration` event to patron notice policies (CIRCSTORE-141)
* Adds blank `request delivery staff slip` template (CIRCSTORE-150)
* Adds reported missing database indexes (CIRCSTORE-139) 
* Provides `anonymize-storage-loans 0.1` interface (CIRCSTORE-137)
* Provides `patron-notice-policy-storage 0.9` interface (CIRCSTORE-141)
* Provides `scheduled-notice-storage 0.3` interface (CIRCSTORE-146)

## 9.1.0 2019-07-24

* Support hyphens in request status when using CQL = relation (CIRCSTORE-138)

## 9.0.0 2019-07-23

* Can store the patron group for the borrower at check out (CIRCSTORE-135)
* loans history is represented differently (due to changes to audit tables in RAML Module Builder, CIRCSTORE-134)
* Upgrades RAML Module Builder to 26.2.1 (CIRCSTORE-134, CIRCSTORE-136)
* Provides `loan-storage 6.1` (CIRCSTORE-134, CIRCSTORE-135)

## 8.1.0 2019-06-10

* Store when a request expires or is cancelled whilst awaiting pickup (CIRCSTORE-127)
* Introduces storage of scheduled notices (CIRSTORE-132)
* Executes the request expiration task every 2 minutes (CIRCSTORE-133)
* Uses databases transactions during request expiration (CIRCSTORE-126)    
* Simplifies default circulation rules and policies (CIRCSTORE-130)
* Removes sample request records (CIRCSTORE-129)
* Upgrades RAML Module Builder to 24.0.0 (CIRCSTORE-122)
* Includes technical metadata in module descriptor (FOLIO-2003)
* Provides `scheduled-notice-storage 0.1` (CIRCSTORE-132)
* Provides `request-storage 8.1` (CIRCSTORE-127)

## 8.0.0 2019-05-07

* Consistent closed due date management setting in sample policies CIRCSTORE-123, CIRCSTORE-120
* Use correct path for loading sample patron notice policies CIRCSTORE-117
* Disallow deletion of patron notice policy when used in rules CIRCSTORE-119


## 7.0.0 2019-03-15

* Expires unfulfilled requests  (CIRCSTORE-108, CIRCSTORE-107, CIRCSTORE-113) 
* Expires requests on the hold shelf (CIRCSTORE-109, CIRCSTORE-107, CIRCSTORE-113)
* Introduces request policies (CIRCSTORE-97)
* Introduces initial patron notice policies (CIRCSTORE-89, CIRCSTORE-90, CIRCSTORE-91, CIRCSTORE-92, CIRCSTORE-93, CIRCSTORE-100, CIRCSTORE-111, CIRCSTORE-112) 
* Renames loan rules storage to circulation rules storage (CIRCSTORE-102)
* Introduces `Closed - Unfilled`, `Closed - Pickup expired` and `Open - In transit` request states ( CIRCSTORE-101, CIRCSTORE-109, CIRCSTORE-110)
* Adds `opening time offset period` to loan policy (CIRCSTORE-64)
* Adds `tags` to requests (CIRCSTORE-99)
* Improves CQL Query performance improvements (CIRCSTORE-114)
* Removes `alternative loan period for items with requests` from loan policy (CIRCSTORE-96)
* Adds `action comment` to loans (CIRCSTORE-87)
* Loads reference and sample records during tenant initialization (CIRCSTORE-103, CIRCSTORE-116, FOLIO-1782)
* Upgrades to `RAML Module Builder 23.11.0`
* Provides `loan-stotage 5.3`
* Provides `circulation-rules-storage 1.0` (instead of `loan-rules-storage 1.0`)
* Provides `loan-policy-storage 2.0`
* Provides `request-storage 3.0`
* Provides `patron-notice-policy-storage 0.7`
* Provides `request-policy-storage 1.0`
* Provides `_tenant 1.2`

## 6.2.0 2018-11-23

* Adds `checkinServicePointId` and `checkoutServicePointId` to a loan (CIRCSTORE-77)
* Adds `pickupServicePointId` to a request (CIRCSTORE-76)
* Uses RAML 1.0 for API documentation (CIRCSTORE-79)
* Provides `loan-storage` 5.2 (CIRCSTORE-77)
* Provides `request-storage` 2.3 (CIRCSTORE-76)

## 6.1.0 2018-09-11

* Can anonymize all closed loans for a single user (CIRCSTORE-73)
* Provides `loan-storage` 5.1 (CIRCSTORE-73)

## 6.0.0 2018-09-09

* Only requires `userId` for open loans (CIRCSTORE-71)
* Defaults loan `status` to `Open` (CIRCSTORE-71)
* Upgrades to RAML Module Builder 19.4.1 (RMB-231)
* Provides `loan-storage` 5.0 (CIRCSTORE-71)

## 5.6.0 2018-08-29

* Use declarative unique index for request queue position, instead of custom snippet (CIRCSTORE-70)
* Upgrades to RAML Module Builder 19.3.1 (CIRCSTORE-70, RMB-176)

## 5.5.0 2018-08-02

* Introduces `position` property for requests (CIRCSTORE-60)
* Provides `request-storage` interface 2.2 (CIRCSTORE-60)

## 5.4.0 2018-07-24

* Add 'metadata' field to loans-policy records (CIRCSTORE-63)
* Add `publicDescription` to cancellation reason record (CIRCSTORE-66)
* Provides `cancellation-reasons` interface 1.1 (CIRCSTORE-66)
* Provides `loan-policy-storage` interface 1.3 (CIRCSTORE-63)

## 5.3.0 2018-07-10

* Upgrades RAML module builder to version 19.1.5, to allow for database connection expiry (CIRCSTORE-62, RMB-154)

## 5.2.0 2018-06-27

* Adds `Closed - Cancelled` request status enum (CIRCSTORE-59)
* Adds cancellation properties to requests (CIRCSTORE-47)
* Introduces cancellation reasons API (CIRCSTORE-57)
* Provides `cancellation-reason-storage` interface 1.0 (CIRCSTORE-57)
* Provides `request-storage` interface 2.1 (CIRCSTORE-59, CIRCSTORE-47)

## 5.1.0 2018-06-25

* Allows `loan-history` endpoint limit/offset to be set by default when query is null (CIRCSTORE-58)
* Provides `staff-slips-storage` interface 1.0 (CIRCSTORE-52)

## 5.0.1 2018-05-01

* Upgrades RAML Module Builder to version 19.0.0 (CIRCSTORE-43, RMB-130)
* Uses generated sources for generated code (CIRCSTORE-43, RMB-130)
* Renames `metaData` property for loans to `metadata` (CIRCSTORE-43)
* Renames `metaData` property for requests to `metadata` (CIRCSTORE-43)
* Renames `metaData` property for fixed due date schedules to `metadata` (CIRCSTORE-43)
* Only allows one loan rules definition at a time (CIRCSTORE-50)
* Provides `loan-storage` interface 4.0 (CIRCSTORE-43)
* Provides `request-storage` interface 2.0 (CIRCSTORE-43)
* Provides `fixed-due-date-schedules-storage` interface 2.0 (CIRCSTORE-43)
* Uses embedded PostgreSQL 10.1 during tests (CIRCSTORE-43, RMB-126)

## 4.6.0 2018-04-18

* Allows snapshot of proxying patron metadata to be stored with a request (CIRCSTORE-46)
* Provides request-storage interface 1.5 (CIRCSTORE-46)

## 4.5.0 2018-03-13

* Adds `systemReturnDate` to `loans` (CIRCSTORE-44)
* Adds `status` property to `requests` (CIRCSTORE-37)
* Adds `proxyUserId` to `requests` (CIRCSTORE-40)
* Fixed due date schedules can now be created with the same `due` and `to` dates (CIRCSTORE-42)
* Validation errors during creation or update of fixed due date schedules are returned in responses (CIRCSTORE-42)
* Provides loan-storage interface 3.5 (CIRCSTORE-44)
* Provides request-storage interface 1.4 (CIRCSTORE-37, CIRCSTORE-40)

## 4.2.0 2018-02-12

* Search loan policies using CQL (CIRCSTORE-37)
* Provides loan-policy-storage interface 1.2 (CIRCSTORE-37)

## 4.1.0 2018-02-09

* Adds `loanPolicyId` property to a loan, to keep the last policy that was applied to this loan (CIRCSTORE-32)
* Provides loan-storage interface 3.4 (CIRCSTORE-32)

## 4.0.0 2017-12-20

* Expose new fixed due date schedule end point (`/fixed-due-date-schedule-storage/fixed-due-date-schedules`, CIRCSTORE-9)
* Allow only one open loan per item (CIRCSTORE-19) - see readme for further details
(note that this constraint is expressed as a major change in implementation version
yet the interface version remains the same)
* Adds relationship between loan policies and fixed due date schedules (CIRCSTORE-29)
* Adds `deliveryAddressTypeId` property to a request for fulfillment to an address (CIRCSTORE-30)
* Adds `itemStatus` property to a loan, in order for it to be included in the action history (CIRCSTORE-27)
* Adds `itemStatus` property to loan history, when provided in requests for loan (CIRCSTORE-27)
* Provides loan-storage interface 3.3 (CIRCSTORE-27)
* Provides request-storage interface 1.2 (CIRCSTORE-30)
* Provides fixed-due-date-schedules-storage interface 1.0 (CIRCSTORE-9)
* Provides loan-policy-storage interface 1.1 (CIRCSTORE-29)

## 3.3.0 2017-10-11

* Allows requests to be searched and sorted via CQL (CIRCSTORE-22)
* Allows snapshot of item metadata to be stored with a request (CIRCSTORE-23)
* Allows snapshot of requesting patron metadata to be stored with a request (CIRCSTORE-24)
* Provides request-storage interface 1.1
* Add "Publish Module Descriptor" step to Jenkinsfile (FOLIO-728)
* Remove old Descriptors (towards FOLIO-701)
* Generates Descriptors at build time from templates in ./descriptors (FOLIO-701, CIRCSTORE-21)
* Adds mod- prefix to names of the built artifacts (FOLIO-813)
* Move loan-policy.json and period.json to ramls/raml-util (CIRC-11)

## 3.2.0 2017-08-17

* Adds `metaData` property to loan (for created and updated information)
* Introduces initial loan requests endpoint (CIRCSTORE-14)
* CIRCSTORE-16 operator_id is missing in loan_history_table
* Introduces proxy user ID for a loan (CIRCSTORE-20)
* Provides loan-storage interface 3.2
* Provides request-storage interface 1.0

## 3.1.0 2017-08-01

* Adds `dueDate` property to loan (using the date-time format)
* Adds `renewalCount` property to loan
* Provides loan-storage interface 3.1
* CIRCSTORE-15 add default sorting to loan action history in case no sorting requested (upgrades to RMB 13.0.1)
* Include implementation version in `id` in Module Descriptor

## 3.0.0 2017-07-17

* Store loan action history for a Loan (CIRCSTORE-12)
* Introduces /loan-history endpoint for the set of historic versions of loans
* Adds required property `action` to loan
* Provides loan-storage interface 3.0

## 2.2.0 2017-07-13

* CIRCSTORE-8 Implement a Loan Rules web service endpoint
* Upgrade to RAML Module Builder 12.1.4: make it run under Windows (RMB-25), fix UpdateSection (RMB-32)
* UTF-8 in maven: project.build.sourceEncoding, project.reporting.outputEncoding

## 2.1.0 2017-07-06

* Provides initial loan policies collection resource

## 2.0.0 2017-06-07

* Upgrade to RAML Module Builder 12.1.2
* Disallow additional properties in loan requests
* Provides loan-storage interface version 2.0

## 1.0.3 2017-04-19

* Fix incorrect version number

## 1.0.2 2017-04-04

* Fix incorrect interface version number

## 1.0.1 2017-04-04

* Add additional permission descriptions

## 1.0.0 2017-03-31

* Use path handlers for Tenant API registration

## 0.1.0 2017-03-09

* Attempt at Docker image building
