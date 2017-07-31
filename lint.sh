#!/usr/bin/env bash

npm install

./node_modules/.bin/raml-cop ramls/loan-storage.raml

./node_modules/.bin/raml-cop ramls/loan-policy-storage.raml

./node_modules/.bin/raml-cop ramls/loan-rules-storage.raml

./node_modules/.bin/raml-cop ramls/request-storage.raml
