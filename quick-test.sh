#!/usr/bin/env bash

./setup-test-db.sh

mvn -q test -Dorg.folio.loan.storage.test.database=external

test_results=$?

if [ $external_test_results != 0 ]; then
  echo '--------------------------------------'
  echo 'BUILD FAILED'
  echo '--------------------------------------'
  exit 1;
else
  ./destroy-test-db.sh

  echo '--------------------------------------'
  echo 'BUILD SUCCEEDED'
  echo '--------------------------------------'
fi
