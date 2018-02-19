# Guide

This is a guide meant to help you both use the `mod-circulation-storage` module 
and enable you to contribute to the module.

## API

### Where the API code lives

The implementation java files live in the 
`/src/main/java/org/folio/rest/impl/` package. These implement the actual 
java interfaces, to be found in the 
`/src/main/java/org/folio/rest/jaxrs/resource/` package, which are 
automatically generated from the `raml` files in `/ramls/`.

Corresponding API tests can be found in the `/src/test/org/folio/rest/api/` package. 

### The API instructions

#### fixed-due-date-schedule

Path (prefix): `/fixed-due-date-schedule-storage/`

The entity and its fields (for _PUT_ and _POST_):
* `ramls/fixed-due-date-schedule.json`
* `src/main/java/org/folio/rest/jaxrs/model/FixedDueDateSchedule.java`

_DELETE_ (produces:`text/plain`)
* all: `fixed-due-date-schedules`
* single: `fixed-due-date-schedules/{id}`

_POST_ (consumes:`application/json`, produces:`application/json`, `text/plain`)
* `fixed-due-date-schedules`

_GET_ (produces:`application/json`, `text/plain`)
* all: `fixed-due-date-schedules`
* single: `fixed-due-date-schedules/{id}`

_PUT_ (comsumes:`application/json`, produces:`text/plain`)
* `fixed-due-date-schedules/{id}`

#### loan-policy

Path (prefix): `/loan-policy-storage/`

The entity and its fields (for PUT and POST):
* `ramls/raml-util/schemas/mod-circulation/loan-policy.json`
* `src/main/java/org/folio/rest/jaxrs/model/LoanPolicy.java`

_DELETE_ (produces:`text/plain`) 
* all: `loan-policies`
* single: `loan-policies/{id}`

_POST_ (consumes:`application/json`, produces:`application/json`, `text/plain`)
* `loan-policies`

_GET_ (produces:`application/json`, `text/plain`)
* all: `loan-policies`
* single: `loan-policies/{id}`

_PUT_ (consumes:`application/json`, produces:`text/plain`)
* `loan-policies/{id}`

#### loan-rules

Path: `/loan-rules-storage`

The entity and its fields (for PUT and POST):
* `ramls/loan-rules.json`
* `src/main/java/org/folio/rest/jaxrs/model/LoanRules.java`

_GET_ (see path, no suffix; produces:`application/json`, `text/plain`)

_PUT_ (see path, no suffix; consumes:`application/json`, produces:`text/plain`))

#### loan

Path (prefix): `/loan-storage/`

The entity and its fields (for PUT and POST):
* `ramls/loan.json`
* `src/main/java/org/folio/rest/jaxrs/model/Loan.java`

_DELETE_ (produces:`text/plain`)
* all: `loans`
* single: `loans/{id}`

_POST_ (consumes:`application/json`, produces:`application/json`, `text/plain`)
* `loans`

_GET_ (produces:`application/json`, `text/plain`)
* all: `loans`
* single: `loans/{id}`
* history: `loan-history`

_PUT_ (comsumes:`application/json`, produces:`application/json`, `text/plain`)
* `fixed-due-date-schedules/{id}`

#### request

Path (prefix): `/request-storage/`

The entity and its fields (for PUT and POST):
* `ramls/request.json`
* `src/main/java/org/folio/rest/jaxrs/model/Request.java`

_DELETE_ (produces:`text/plain`)
* all: `requests`
* single: `requests/{id}`

_POST_ (consumes:`application/json`, produces:`application/json`, `text/plain`)
* `requests`

_GET_ (produces:`application/json`, `text/plain`)
* all: `requests`
* single: `requests/{id}`

_PUT_ (comsumes:`application/json`, produces:`text/plain`)
* `requests/{id}`

