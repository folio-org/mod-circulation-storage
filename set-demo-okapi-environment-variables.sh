#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

create_environment_variable() {
  environment_value_json_file=${1:-}

  environment_json=$(cat ${environment_value_json_file})

  curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d "${environment_json}" \
     "${okapi_proxy_address}/_/env"
}

# setup Okapi environment variables
create_environment_variable ./okapi-setup/environment/db-host.json
create_environment_variable ./okapi-setup/environment/db-port.json

