#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

./create-tenant.sh

./setup-demo-db.sh

