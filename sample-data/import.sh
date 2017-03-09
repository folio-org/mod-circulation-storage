#!/usr/bin/env bash

loan_storage_address=${1:-http://localhost:9130/loan-storage/loans}
tenant=${2:-demo_tenant}

for f in ./loans/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${loan_storage_address}"
done

