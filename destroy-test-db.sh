#!/usr/bin/env bash

host=${1:-localhost}
port=${2:-5432}
executing_user=${3:-$USER}
executing_password=${4:-}

cd database-setup

./destroy-db.sh loan_test test_tenant_circulation_storage loan_test_admin ${host} ${port} ${executing_user} ${executing_password}

./drop-role.sh test_tenant_circulation_storage

cd ..
