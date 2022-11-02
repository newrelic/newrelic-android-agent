#!/usr/bin/env bash

source `dirname $0`/artifact-validations.sh

setup_repo

AWS="$(which aws)"
NR_S3_DOWNLOAD_URI="s3://nr-downloads-main/android_agent"

##
# Validate staged artifacts in download site
#
[[ -z ${AGENT_BUILD} ]] && read_version_meta ant
validate_downloads_stage

echo Deploying Android Agent ${AGENT_VERSION}.${AGENT_BUILD} artifacts to production:

# Copies agent zip artifact to  downloads-main/android_agent/ant/
${AWS} s3 cp ${NR_S3_DOWNLOAD_URI}/ant-v5/NewRelic_Android_Agent_${AGENT_VERSION}.zip ${NR_S3_DOWNLOAD_URI}/ant/

# Copies Maven/Gradle plugins artifacts to downloads-main/android_agent/jcenter/
${AWS} s3 cp --recursive ${NR_S3_DOWNLOAD_URI}/jcenter-staging/${AGENT_VERSION} ${NR_S3_DOWNLOAD_URI}/jcenter/${AGENT_VERSION}

##
# Validate final locations
##
[[ -z ${AGENT_BUILD} ]] && die "Cannot validate deploy without a build number"

# S3 can take some time to propagate, so wait a while before validation from this script
sleep 180
PROD_DEPLOY="true" validate_downloads_prod