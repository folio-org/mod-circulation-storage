#!/usr/bin/env bash

build=${1:-"build"}
storage=${2:-"external"}
okapi_proxy_address=${3:-http://localhost:9130}

tenant_id="demo_tenant"

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

if [ "${build}" = "build" ]; then
  echo "Package circulation storage module"
  mvn package -q -Dmaven.test.skip=true || exit 1
elif [ "${build}" = "no-build" ]; then
  echo "Skipping building of loan storage module"
else
  echo "Unknown build directive: ${build}"
  exit 1
fi

./create-tenant.sh

if [ "${storage}" = "external" ]; then
  echo "Running circulation storage module using external PostgreSQL storage"

  ./set-demo-okapi-environment-variables.sh

  ./setup-demo-db.sh

  deployment_descriptor="target/DeploymentDescriptor-environment.json"

elif [ "${storage}" = "embedded" ]; then
  echo "Running circulation storage module using embedded PostgreSQL storage"

  deployment_descriptor="target/DeploymentDescriptor.json"

else
  echo "Unknown storage mechanism: ${storage}"
  exit 1
fi

./okapi-registration/managed-deployment/register.sh \
  ${okapi_proxy_address} \
  ${tenant_id} \
  ${deployment_descriptor}
