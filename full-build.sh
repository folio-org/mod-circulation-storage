#!/usr/bin/env bash

./setup-test-db.sh

mvn -q test \
    -Dorg.folio.loan.storage.test.database=external

external_test_results=$?

mvn -q test \
    -Dorg.folio.loan.storage.test.database=embedded

embedded_test_results=$?

mvn package -Dmaven.test.skip=true

echo "External database tests exit code: ${external_test_results}"
echo "Embedded database tests exit code: ${embedded_test_results}"

if [ $embedded_test_results != 0 ] || [ $external_test_results != 0 ]; then
    echo '--------------------------------------'
    echo 'BUILD FAILED'
    echo '--------------------------------------'
    exit 1;
fi
