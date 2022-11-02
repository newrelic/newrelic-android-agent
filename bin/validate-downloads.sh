#!/usr/bin/env bash

source `dirname $0`/artifact-validations.sh

PROD_DEPLOY=${PROD_DEPLOY:-"false"}

if [[ ${PROD_DEPLOY} == "true" ]] ; then
    validate_downloads_prod
else
    validate_downloads_stage
fi
