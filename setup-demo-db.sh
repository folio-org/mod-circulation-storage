#!/usr/bin/env bash

host=${1:-localhost}
port=${2:-5432}
executing_user=${3:-$USER}
executing_password=${4:-}

cd database-setup

./setup-db.sh loan_demo loan_demo_admin ${host} ${port} ${executing_user} ${executing_password}

cd ..
