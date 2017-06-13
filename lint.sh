#!/usr/bin/env bash

npm install

./node_modules/.bin/eslint ramls/loan-storage.raml

./node_modules/.bin/eslint ramls/loan-policy-storage.raml
