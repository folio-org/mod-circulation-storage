# mod-circulation-storage

Copyright (C) 2017-2018 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Goal

FOLIO compatible persistent storage of loans and requests.

# Implementation Specific Behaviours

As a FOLIO interface is limited to describing the protocol (HTTP endpoints and 
the structure of expected requests and responses) between the client and server 
there are behaviors which go beyond this definition and are implementation specific,
some of which are noted below.

## Only one Open Loan per Item 

This implementation introduces the constraint that only one loan can be open for a
given item. Requests (either POST or PUT) which could result in two loans with the
same `itemId` and the `status` of `Open` should be rejected with a error (422) response

# Prerequisites

# Required

- Java 8 JDK
- Maven 3.3.9
- PostgreSQL 9.6.1 (running and listening on the default port, logged in user must have admin rights)

## Optional

- Node.js 6.4 (for API linting)
- NPM 3.10 (for API linting)
- Python 3.6.0 (for un-registering module during managed deployment scripts)

# Preparation

## Git Submodules

There are some common RAML definitions that are shared between FOLIO projects via Git submodules.

To initialise these please run `git submodule init && git submodule update` in the root directory.

If these are not initialised, the inventory-storage module will fail to build correctly, and other operations may also fail.

More information is available on the [developer site](http://dev.folio.org/doc/setup#update-git-submodules).

## PostgreSQL

Run the `setup-test-db.sh` script in the inventory-storage directory to setup Postgres with a database to be used in tests.
This is only required to run tests against an external Postgres instance, the default is to use an embedded Postgres instance.

# Common activities

## Checking the RAML and JSON.Schema definitions

run `./lint.sh` to validate the RAML and JSON.Schema descriptions of the API (requires node.js and NPM)

# Frequently Asked Questions

## Dates and times

A loan has the date and time when and item was leant to a user and when it was returned. The module expects them to be represented in [RFC3339 format](https://tools.ietf.org/html/rfc3339#section-3).

At the moment, the JSON.schema for loan does provide validation for these (it will eventually use the date-time format included in the standard when this is supported by the RAML module builder).

# Additional Information

Other [modules](http://dev.folio.org/source-code/#server-side).

See project [CIRCSTORE](https://issues.folio.org/browse/CIRCSTORE)
at the [FOLIO issue tracker](http://dev.folio.org/community/guide-issues).

Other FOLIO Developer documentation is at [dev.folio.org](http://dev.folio.org/)
