#!/usr/bin/env bash

./delete-tenant.sh

./destroy-demo-db.sh

if  which python3
then
  pip3 install requests

  python3 ./okapi-setup/environment/clear-environment-variables.py

else
  echo "Install Python3 to remove environment variables from Okapi automatically"
fi
