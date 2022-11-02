#!/usr/bin/env bash

source `dirname $0`/artifact-validations.sh

if [[ ${PROD_DEPLOY} == "true" ]] ; then
    validate_sonatype_prod
else
    validate_sonatype_stage
fi
