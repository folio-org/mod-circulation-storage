#!/usr/bin/env bash

loan_storage_address=${1:-http://localhost:9130/loan-storage/loans}
loan_policy_storage_address=${2:-http://localhost:9130/loan-policy-storage/loan-policies}
loan_rules_storage_address=${3:-http://localhost:9130/loan-rules-storage}
request_storage_address=${4:-http://localhost:9130/request-storage/requests}
tenant=${5:-demo_tenant}

for f in ./loan-policies/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${loan_policy_storage_address}"
done

curl -w '\n' -X PUT -D - \
     -H "Content-type: application/json" \
     -H "X-Okapi-Tenant: ${tenant}" \
     -d @./loan-rules/use-example-for-everything.json \
     "${loan_rules_storage_address}"

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

