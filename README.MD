# mod-circulation-storage

Copyright (C) 2017-2020 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Goal

FOLIO compatible persistent storage of loans, loan policies, circulation rules,
requests, and fixed due date schedules

## API

### Where the API code lives

The implementation java files live in the
[`/src/main/java/org/folio/rest/impl`](src/main/java/org/folio/rest/impl)
package. These implement the actual
java interfaces, to be found in the
[`/src/main/java/org/folio/rest/jaxrs/resource`](src/main/java/org/folio/rest/jaxrs/resource)
package, which are automatically generated from the `raml` files in
[`/ramls/`](ramls).

Corresponding API tests can be found in the
[`/src/test/java/org/folio/rest/api`](src/test/java/org/folio/rest/api)
package.

### API documentation

The API documentation generated from the `*.raml` files can be found online at
[https://dev.folio.org/reference/api/#mod-circulation-storage](https://dev.folio.org/reference/api/#mod-circulation-storage)


## Implementation Specific Behaviours

As a FOLIO interface is limited to describing the protocol (HTTP endpoints and
the structure of expected requests and responses) between the client and server
there are behaviors which go beyond this definition and are implementation specific,
some of which are noted below.

### Only one Open Loan per Item

This implementation introduces the constraint that only one loan can be open for a
given item.

HTTP Requests (either POST or PUT) which could result in two loans with the
same `itemId` and the `status` of `Open` should be rejected with a error (422) response

### Unique Position in per-item Request Queue

This implementation introduces the constraint that only one open (e.g. `Open - Not yet filled`) request
for a given item can be at a particular position in the request queue for that item.

HTTP Requests (either POST or PUT) which could result two open requests with the
same `itemId` and `position` should be rejected with a error (422) response

### Known Limitations

#### Anonymization SQL

At the moment, this is constructed with string concatenation (it will hopefully soon be replaced by prepared statements).

The `userId` parameter is checked to take the form of a UUID to try to reduce the exposure of this.

## Prerequisites

## Required

- Java 11 JDK
- Maven 3.3.9
- PostgreSQL 9.6.1 (running and listening on the default port, logged in user must have admin rights), with the following extensions
  - pgcrypto
  - unaccent
  - pg_trgm

### Optional

- Python 3.6.0 (for un-registering module during managed deployment scripts, and the lint-raml tools)

## Preparation

### Git Submodules

There are some common RAML definitions that are shared between FOLIO projects via Git submodules.

To initialise these please run `git submodule init && git submodule update` in the root directory.

If these are not initialised, the inventory-storage module will fail to build correctly, and other operations may also fail.

More information is available on the [developer site](https://dev.folio.org/guides/developer-setup/#update-git-submodules).

### PostgreSQL

Run the `setup-test-db.sh` script in the inventory-storage directory to setup Postgres with a database to be used in tests.
This is only required to run tests against an external Postgres instance, the default is to use an embedded Postgres instance.

## Common activities

### Checking the RAML and JSON.Schema definitions

Follow the [guide](https://dev.folio.org/guides/raml-cop/) to use raml-cop to assess RAML, schema, and examples.

## Frequently Asked Questions

### Dates and times

A loan has the date and time when and item was leant to a user and when it was returned. The module expects them to be represented in [RFC3339 format](https://tools.ietf.org/html/rfc3339#section-3).

At the moment, the JSON.schema for loan does provide validation for these (it will eventually use the date-time format included in the standard when this is supported by the RAML module builder).

## Additional Information

Other [modules](https://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [CIRCSTORE](https://issues.folio.org/browse/CIRCSTORE)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-circulation-storage).

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-circulation-storage).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-circulation-storage/).

## Design notes

### Request status transition

There is database trigger for request update. It works in the following way:

Given a request with status 'Open - Awaiting pickup'<br/>
When the request is updated and new status is 'Closed - Pickup expired' or 'Closed - Cancelled'<br/>
Then an update trigger adds the property 'awaitingPickupRequestClosedDate' to the request JSONB<br/>

Such behavior is required by Expired Holds Report CSV functionality in mod-circulation<br/>
See [CIRC-320](https://issues.folio.org/browse/CIRC-320)

### Requests Batch API

In order to go through `itemId`-`position` constraint for **request** table we're removing all positions
for requests as the first operation of batch, before executing the updates.

Let's say we have following requests in batch package:
* *Request A*;
* *Request B*;
* *Request C*.

Then, in order to execute them successfully and do not get constraint violation we're
removing positions for these request, so we will do:
1. `UPDATE requests SET jsonb = jsonb - 'position' WHERE id IN (A, B, C)`;
1. `UPDATE requests SET jsonb = A WHERE id = A`;
1. `UPDATE requests SET jsonb = B WHERE id = B`;
1. `UPDATE requests SET jsonb = C WHERE id = C`;
