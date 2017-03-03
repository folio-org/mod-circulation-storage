#!/usr/bin/env bash

host=${1:-localhost}
port=${2:-5432}
executing_user=${3:-$USER}
executing_password=${4:-}

cd database-setup

./destroy-db.sh test test_tenant_loan_storage loan_test_admin ${host} ${port} ${executing_user} ${executing_password}

cd ..
