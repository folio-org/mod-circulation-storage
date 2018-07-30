## 5.5.0 Unreleased

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
