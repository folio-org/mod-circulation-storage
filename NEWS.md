## 3.1.0 Unreleased

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
