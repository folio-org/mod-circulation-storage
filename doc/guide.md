# Guide

This is a guide meant to help you both use the `mod-circulation-storage` module 
and enable you to contribute to the module.

## API

### Where the API code lives

The implementation java files live in the 
[`/src/main/java/org/folio/rest/impl`](../src/main/java/org/folio/rest/impl) 
package. These implement the actual 
java interfaces, to be found in the 
[`/src/main/java/org/folio/rest/jaxrs/resource`](../src/main/java/org/folio/rest/jaxrs/resource)
package, which are automatically generated from the `raml` files in
[`/ramls/`](../ramls).

Corresponding API tests can be found in the
[`/src/test/java/org/folio/rest/api`](../src/test/java/org/folio/rest/api) 
package.

### API documentation

The API documentation generated from the `*.raml` files can be found online at
[https://dev.folio.org/doc/api/](https://dev.folio.org/doc/api/)
