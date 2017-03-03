#!/usr/bin/env bash

instance_id=${1:-}
okapi_proxy_address=${2:-http://localhost:9130}

tenant_id="demo_tenant"
module_id="loan-storage"

./okapi-registration/managed-deployment/unregister.sh \
  ${module_id} \
  ${okapi_proxy_address} \
  ${tenant_id}

./delete-tenant.sh

if  which python3
then
  pip3 install requests

  python3 ./okapi-setup/environment/clear-environment-variables.py

else
  echo "Install Python3 to remove environment variables from Okapi automatically"
fi

./destroy-demo-db.sh
