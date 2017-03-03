#!/usr/bin/env bash

storage=${1:-"external"}
okapi_proxy_address=${2:-http://localhost:9130}

tenant_id="demo_tenant"

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Package loan storage module"
mvn package -q -Dmaven.test.skip=true || exit 1

./create-tenant.sh

if [ "${storage}" = "external" ]; then
  echo "Running Inventory Storage module using external PostgreSQL storage"

  ./set-demo-okapi-environment-variables.sh

  ./setup-demo-db.sh

  deployment_descriptor="DeploymentDescriptor-environment.json"

elif [ "${storage}" = "embedded" ]; then
  echo "Running Inventory Storage module using embedded PostgreSQL storage"

  deployment_descriptor="DeploymentDescriptor.json"

else
  echo "Unknown storage mechanism: ${storage}"
  exit 1
fi

./okapi-registration/managed-deployment/register.sh \
  ${okapi_proxy_address} \
  ${tenant_id} \
  ${deployment_descriptor}
