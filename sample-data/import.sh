#!/usr/bin/env bash

loan_storage_address=${1:-http://localhost:9130/loan-storage/loans}
loan_policy_storage_address=${2:-http://localhost:9130/loan-policy-storage/loan-policies}
tenant=${3:-demo_tenant}

for f in ./loans/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${loan_storage_address}"
done

for f in ./loan-policies/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${loan_policy_storage_address}"
done

