#!/usr/bin/env bash

okapi_proxy_address=${2:-http://localhost:9130}
loan_storage_address=${2:-http://localhost:9130/loan-storage/loans}
loan_history_storage_address=${3:-http://localhost:9130/loan-storage/loan-history}
request_storage_address=${4:-http://localhost:9130/request-storage/requests}
tenant=${5:-demo_tenant}

echo "Does not delete loan history"

curl -w '\n' -X DELETE -D - \
     -H "X-Okapi-Tenant: ${tenant}" \
     "${loan_storage_address}"

curl -w '\n' -X DELETE -D - \
     -H "X-Okapi-Tenant: ${tenant}" \
     "${request_storage_address}"


