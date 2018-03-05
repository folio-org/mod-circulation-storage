#!/bin/bash

#
# import mod-circulation-storage reference data
#

#set -x

type curl >/dev/null 2>&1 || { echo >&2 "$0: curl is required but it's not installed"; exit 1; }

usage() {
   cat << EOF
Usage: ${0##*/} -o OKAPI_URL -t TENANT -a
       -o Okapi_URL (required)
       -t tenant (required)
       -d directory_to_reference_data (optional) Default: '.'
       -a x-okapi-token  (optional) Default: (no auth)
EOF
}


OPTIND=1 

while getopts "h?a:o:t:d:" opt 
do
    case "$opt" in
    h|\?)
        usage
        exit 1
        ;;
    o)  
        okapiUrl=$OPTARG
        ;;
    t)  
        tenant=$OPTARG
        ;;
    d)  
        dataDirs+=("$OPTARG")
        ;;
    a)  
        auth_required=true
        authToken=$OPTARG
        ;;  
    *)  
        usage >&2
        exit 1
        ;;
    esac
done

shift $((OPTIND -1))

okapiUrl=${okapiUrl:-http://localhost:9130}
tenant=${tenant:-demo_tenant}
dataDirs=${dataDirs:-'.'}
modEndpoints='loan-policy-storage/loan-policies loan-rules-storage'

for dir in "${dataDirs[@]}"; 
do
  for endpoint in $modEndpoints 
  do 
    if [ -d "${dir}/${endpoint}" ]; then
      if [[ "$endpoint" =~ ^loan-rules-storage$ ]]; then
        method=PUT
      else
        method=POST
      fi

      json=$(ls ${dir}/${endpoint}/*.json)
      for j in $json 
      do 
        if [ "$auth_required" = true ]; then
           curl  --connect-timeout 10 -w '\n' \
             -H 'Content-type: application/json' \
             -H 'Accept: application/json, text/plain' \
             -H "X-Okapi-Tenant: $tenant" \
             -H "X-Okapi-Token: $authToken" \
             -X $method -d @$j ${okapiUrl}/${endpoint}
        else 
           curl  --connect-timeout 10 -w '\n' \
             -H 'Content-type: application/json' \
             -H 'Accept: application/json, text/plain' \
             -H "X-Okapi-Tenant: $tenant" \
             -X $method -d @$j ${okapiUrl}/${endpoint}
        fi
      done
    fi
  done
done

