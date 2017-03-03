#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

tenant_id="demo_tenant"
deployment_descriptor="DeploymentDescriptor-environment.json"

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Package loan storage module"
mvn package -q -Dmaven.test.skip=true || exit 1

./create-tenant.sh

./set-demo-okapi-environment-variables.sh

./setup-demo-db.sh

./okapi-registration/managed-deployment/register.sh \
  ${okapi_proxy_address} \
  ${tenant_id} \
  ${deployment_descriptor}
