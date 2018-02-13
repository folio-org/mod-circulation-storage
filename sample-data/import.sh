#!/usr/bin/env bash

loan_storage_address=${1:-http://localhost:9130/loan-storage/loans}
request_storage_address=${2:-http://localhost:9130/request-storage/requests}
tenant=${3:-demo_tenant}

for f in ./loans/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${loan_storage_address}"
done

for f in ./requests/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${request_storage_address}"
done

