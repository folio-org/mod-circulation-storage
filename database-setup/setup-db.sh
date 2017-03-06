#!/usr/bin/env bash

database_name=${1:-}
admin_user_name=${2:-}
host=${3:-localhost}
port=${4:-5432}
executing_user=${5:-$USER}
executing_password=${6:-}

admin_password="admin"

./create-admin-role.sh ${admin_user_name} ${admin_password} ${host} ${port} ${executing_user} ${executing_password}
./create-db.sh ${database_name} ${admin_user_name} ${host} ${port} ${executing_user} ${executing_password}
